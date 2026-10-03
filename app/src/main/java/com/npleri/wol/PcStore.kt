package com.npleri.wol

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** La PC a controlar. `mac` vacía = todavía no configurada; `token` vacío = sin agente; `relayKey` vacía = sin relé. */
data class Pc(
    val name: String = "PC",
    val mac: String = "",
    val host: String = "",
    val port: Int = 9,
    val token: String = "",
    val relayKey: String = "",
    val remoteHost: String = "",
) {
    /** Direcciones donde buscar la PC: la de casa y, si está cargada, la de Tailscale. */
    val hosts: List<String> get() = listOf(host, remoteHost).filter { it.isNotBlank() }.distinct()
}

private val Context.store by preferencesDataStore("pc")
private val NAME = stringPreferencesKey("name")
private val MAC = stringPreferencesKey("mac")
private val HOST = stringPreferencesKey("host")
private val PORT = intPreferencesKey("port")
private val TOKEN = stringPreferencesKey("token_enc")
private val RELAY = stringPreferencesKey("relay_enc")
private val REMOTE = stringPreferencesKey("remote_host")

fun Context.pcFlow(): Flow<Pc> = store.data.map {
    Pc(
        it[NAME] ?: "PC", it[MAC] ?: "", it[HOST] ?: "", it[PORT] ?: 9,
        Secrets.decrypt(it[TOKEN] ?: ""), Secrets.decrypt(it[RELAY] ?: ""), it[REMOTE] ?: "",
    )
}.flowOn(Dispatchers.IO)

suspend fun Context.savePc(pc: Pc) = withContext(Dispatchers.IO) {
    val token = Secrets.encrypt(pc.token)
    val relay = Secrets.encrypt(pc.relayKey)
    store.edit {
        it[NAME] = pc.name
        it[MAC] = pc.mac
        it[HOST] = pc.host
        it[PORT] = pc.port
        it[TOKEN] = token
        it[RELAY] = relay
        it[REMOTE] = pc.remoteHost
    }
}
