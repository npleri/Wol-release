package com.npleri.wol

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** La PC a controlar. `mac` vacía = todavía no configurada. */
data class Pc(val name: String = "PC", val mac: String = "", val host: String = "", val port: Int = 9)

private val Context.store by preferencesDataStore("pc")
private val NAME = stringPreferencesKey("name")
private val MAC = stringPreferencesKey("mac")
private val HOST = stringPreferencesKey("host")
private val PORT = intPreferencesKey("port")

fun Context.pcFlow(): Flow<Pc> = store.data.map {
    Pc(it[NAME] ?: "PC", it[MAC] ?: "", it[HOST] ?: "", it[PORT] ?: 9)
}

suspend fun Context.savePc(pc: Pc) {
    store.edit {
        it[NAME] = pc.name
        it[MAC] = pc.mac
        it[HOST] = pc.host
        it[PORT] = pc.port
    }
}
