package com.npleri.wol

import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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

/** Token del agente: 32 caracteres hex; se aceptan guiones, espacios y mayúsculas. Devuelve null si es inválido. */
fun normalizeToken(text: String): String? {
    val hex = text.filter { !it.isWhitespace() && it != '-' }.lowercase()
    return hex.takeIf { it.isEmpty() || (it.length == 32 && it.all { c -> c.digitToIntOrNull(16) != null }) }
}

private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

/** Tema de ntfy derivado de la clave del relé. Debe coincidir con relay-esp32/src/main.cpp. */
fun relayTopic(key: String): String =
    "wol-" + MessageDigest.getInstance("SHA-256").digest("topic:$key".toByteArray()).toHex().take(32)

/** Orden de encendido firmada: "wake.<unix-ts>.<HMAC-SHA256(clave, "wake.<unix-ts>")>". */
fun relayMessage(key: String, unixSeconds: Long): String {
    val payload = "wake.$unixSeconds"
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA256")) }
    return payload + "." + mac.doFinal(payload.toByteArray()).toHex()
}

/** Dirección de broadcast IPv4 de la subred: ip OR máscara invertida. */
fun broadcastOf(ip: ByteArray, prefix: Int): ByteArray {
    val hostBits = if (prefix >= 32) 0 else -1 ushr prefix
    return ByteBuffer.allocate(4).putInt(ByteBuffer.wrap(ip).int or hostBits).array()
}
