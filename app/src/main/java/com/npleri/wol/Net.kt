package com.npleri.wol

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.HttpURLConnection
import org.json.JSONObject

/** Envía el paquete mágico 3 veces al broadcast de la red actual y a 255.255.255.255. Devuelve los destinos que funcionaron. */
suspend fun sendWake(context: Context, mac: ByteArray, port: Int): List<InetAddress> = withContext(Dispatchers.IO) {
    val packet = magicPacket(mac)
    val targets = listOfNotNull(subnetBroadcast(context), InetAddress.getByName("255.255.255.255")).distinct()
    val sent = mutableSetOf<InetAddress>()
    var lastError: IOException? = null
    DatagramSocket().use { socket ->
        socket.broadcast = true
        repeat(3) {
            for (target in targets) {
                try {
                    socket.send(DatagramPacket(packet, packet.size, target, port))
                    sent += target
                } catch (e: IOException) {
                    lastError = e
                }
            }
            delay(100)
        }
    }
    if (sent.isEmpty()) throw lastError ?: IOException("No se pudo enviar")
    targets.filter { it in sent }
}

private fun subnetBroadcast(context: Context): InetAddress? {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val link = cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
        ?.firstOrNull { it.address is Inet4Address } ?: return null
    return InetAddress.getByAddress(broadcastOf(link.address.address, link.prefixLength))
}

const val AGENT_PORT = 47800
private const val NTFY_URL = "https://ntfy.sh"

/** Pide al relé (ESP32 en la red de casa) que mande el paquete mágico. Sirve desde cualquier red. */
suspend fun sendRelayWake(key: String) = withContext(Dispatchers.IO) {
    val conn = URL("$NTFY_URL/${relayTopic(key)}").openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.doOutput = true
        conn.outputStream.use { it.write(relayMessage(key, System.currentTimeMillis() / 1000).toByteArray()) }
        if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
    } finally {
        conn.disconnect()
    }
}

class AgentException(val code: Int) : IOException("HTTP $code")

data class Agent(val hostname: String, val user: String?, val warnings: List<String>)

/** online: la PC responde; agent: datos del agente si respondió con el token; unauthorized: token rechazado; host: por dónde respondió. */
data class Probe(val online: Boolean, val agent: Agent? = null, val unauthorized: Boolean = false, val host: String = "")

// Los sondeos corren en un scope propio: si una llamada de red se cuelga, el tope de tiempo la abandona
// y la pantalla sigue actualizándose en vez de quedar con un estado viejo.
private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private const val PROBE_TIMEOUT_MS = 8000L

/** Consulta todas las direcciones de la PC a la vez y se queda con la mejor respuesta. */
suspend fun probe(pc: Pc): Probe = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
    probeScope.async {
        val results = pc.hosts.map { host -> async { probeHost(pc, host) } }.awaitAll()
        results.firstOrNull { it.agent != null }
            ?: results.firstOrNull { it.unauthorized }
            ?: results.firstOrNull { it.online }
            ?: Probe(false)
    }.await()
} ?: Probe(false)

/** Primero pregunta al agente (si hay token); si no contesta, cae al sondeo TCP. */
private suspend fun probeHost(pc: Pc, host: String): Probe {
    if (pc.token.isNotEmpty()) {
        try {
            val status = agentCall(host, pc.token, "GET", "status")
            val warnings = status.optJSONArray("warnings")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
            val user = status.optString("loggedInUser").takeIf { it.isNotEmpty() && it != "null" }
            return Probe(true, Agent(status.optString("hostname"), user, warnings), host = host)
        } catch (e: AgentException) {
            if (e.code == 401) return Probe(true, unauthorized = true, host = host)
        } catch (e: IOException) {
            // Agente apagado o inalcanzable: seguimos con el sondeo TCP.
        }
    }
    return Probe(isOnline(host), host = host)
}

/** Acciones de la lista blanca del agente: shutdown, restart, sleep, hibernate, cancel. */
suspend fun power(host: String, token: String, action: String) {
    withContext(Dispatchers.IO) { agentCall(host, token, "POST", "power/$action") }
}

// ponytail: HTTP sin TLS; el token viaja en claro dentro de la LAN (por Tailscale va cifrado). HTTPS con certificado fijado llega con el QR (Fase 3).
private fun agentCall(host: String, token: String, method: String, path: String): JSONObject {
    val conn = URL("http://$host:$AGENT_PORT/api/v1/$path").openConnection() as HttpURLConnection
    try {
        conn.requestMethod = method
        conn.connectTimeout = 1500
        conn.readTimeout = 3000
        conn.setRequestProperty("Authorization", "Bearer $token")
        // Sin keep-alive: una conexión reutilizada queda muerta cuando la PC se suspende o cambia la red.
        conn.setRequestProperty("Connection", "close")
        if (method == "POST") {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write("{}".toByteArray()) }
        }
        if (conn.responseCode !in 200..299) throw AgentException(conn.responseCode)
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        return runCatching { JSONObject(body) }.getOrDefault(JSONObject())
    } finally {
        conn.disconnect()
    }
}

// Sondeo TCP para saber si la PC está encendida aunque no tenga el agente.
private val PROBE_PORTS = listOf(445, 135, 139, 3389, 47989, AGENT_PORT)

suspend fun isOnline(host: String): Boolean = withContext(Dispatchers.IO) {
    val address = try {
        InetAddress.getByName(host)
    } catch (e: IOException) {
        return@withContext false
    }
    PROBE_PORTS.map { port -> async { answers(address, port) } }.awaitAll().any { it }
}

private fun answers(address: InetAddress, port: Int): Boolean = try {
    Socket().use { it.connect(InetSocketAddress(address, port), 800) }
    true
} catch (e: ConnectException) {
    // Conexión rechazada = el equipo respondió, así que está encendido.
    e.message?.contains("ECONNREFUSED") == true
} catch (e: IOException) {
    false
}
