package com.npleri.wol

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

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

// ponytail: estado por sondeo TCP a puertos típicos de Windows y Sunshine; reemplazar por GET /api/v1/status cuando exista el agente.
private val PROBE_PORTS = listOf(445, 135, 139, 3389, 47989, 47800)

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
