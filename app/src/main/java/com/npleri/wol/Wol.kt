package com.npleri.wol

import java.nio.ByteBuffer

/** Acepta "AA:BB:CC:DD:EE:FF", "AA-BB-…", "AABB.CCDD.EEFF" o sin separadores. */
fun parseMac(text: String): ByteArray? {
    val hex = text.filter { it.isLetterOrDigit() }
    if (hex.length != 12 || !hex.all { it.digitToIntOrNull(16) != null }) return null
    return ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

fun formatMac(mac: ByteArray): String = mac.joinToString(":") { "%02X".format(it) }

/** 6 × 0xFF seguidos de la MAC repetida 16 veces (102 bytes). */
fun magicPacket(mac: ByteArray): ByteArray {
    require(mac.size == 6)
    return ByteArray(6) { 0xFF.toByte() } + ByteArray(16 * 6) { mac[it % 6] }
}

/** Dirección de broadcast IPv4 de la subred: ip OR máscara invertida. */
fun broadcastOf(ip: ByteArray, prefix: Int): ByteArray {
    val hostBits = if (prefix >= 32) 0 else -1 ushr prefix
    return ByteBuffer.allocate(4).putInt(ByteBuffer.wrap(ip).int or hostBits).array()
}
