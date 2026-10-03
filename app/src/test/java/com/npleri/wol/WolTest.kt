package com.npleri.wol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WolTest {
    private val mac = byteArrayOf(0x00, 0x11, 0x22, 0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())

    @Test
    fun parseMac() {
        listOf("00:11:22:AA:BB:CC", "00-11-22-aa-bb-cc", "0011.22AA.BBCC", "001122aabbcc").forEach {
            assertArrayEquals(it, mac, parseMac(it))
        }
        listOf("", "00:11:22:AA:BB", "00:11:22:AA:BB:CC:DD", "G0:11:22:AA:BB:CC").forEach {
            assertNull(it, parseMac(it))
        }
        assertEquals("00:11:22:AA:BB:CC", formatMac(mac))
    }

    @Test
    fun magicPacket() {
        val packet = magicPacket(mac)
        assertEquals(102, packet.size)
        assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, packet.copyOfRange(0, 6))
        for (i in 0 until 16) assertArrayEquals(mac, packet.copyOfRange(6 + i * 6, 12 + i * 6))
    }

    @Test
    fun normalizeToken() {
        val plain = "0123456789abcdef0123456789abcdef"
        assertEquals(plain, normalizeToken(plain))
        assertEquals(plain, normalizeToken("0123-4567-89AB-CDEF-0123-4567-89ab-cdef"))
        assertEquals(plain, normalizeToken(" 0123 4567 89ab cdef 0123 4567 89ab cdef "))
        assertEquals("", normalizeToken(""))
        assertNull(normalizeToken("0123"))
        assertNull(normalizeToken("0123456789abcdef0123456789abcdeg"))
    }

    @Test
    fun relayProtocol() {
        val key = "00112233445566778899aabbccddeeff"
        assertEquals("wol-6220d558b55b46d3321f1909b9d1e78c", relayTopic(key))
        assertEquals(
            "wake.1700000000.f1faaf00951d473a7e23cd03f82060f0adbe5e9e5642dae90972aee051ee106f",
            relayMessage(key, 1700000000),
        )
    }

    @Test
    fun broadcastOf() {
        fun bc(ip: String, prefix: Int) = broadcastOf(ip.split(".").map { it.toInt().toByte() }.toByteArray(), prefix)
            .joinToString(".") { (it.toInt() and 0xFF).toString() }
        assertEquals("192.168.1.255", bc("192.168.1.20", 24))
        assertEquals("10.0.255.255", bc("10.0.3.4", 16))
        assertEquals("172.16.0.7", bc("172.16.0.5", 30))
        assertEquals("192.168.1.1", bc("192.168.1.1", 32))
        assertEquals("255.255.255.255", bc("1.2.3.4", 0))
    }
}
