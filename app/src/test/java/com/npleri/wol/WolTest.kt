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
