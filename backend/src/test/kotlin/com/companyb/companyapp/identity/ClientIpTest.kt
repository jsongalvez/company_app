package com.companyb.companyapp.identity

import kotlin.test.Test
import kotlin.test.assertEquals

class ClientIpTest {
    private val proxy = ClientIp.parseTrusted("10.0.2.2")

    @Test
    fun `a trusted peer's forwarded client is used`() {
        assertEquals("203.0.113.9", ClientIp.resolve("10.0.2.2", "203.0.113.9", proxy))
    }

    @Test
    fun `an untrusted peer's forwarded header is ignored`() {
        assertEquals("198.51.100.4", ClientIp.resolve("198.51.100.4", "203.0.113.9", proxy))
    }

    @Test
    fun `the entry a trusted proxy appended wins over what the client sent`() {
        assertEquals("203.0.113.9", ClientIp.resolve("10.0.2.2", "1.2.3.4, 203.0.113.9", proxy))
    }

    @Test
    fun `trusted hops at the end of the chain are skipped`() {
        val twoHops = ClientIp.parseTrusted("10.0.2.2,10.0.2.3")
        assertEquals("203.0.113.9", ClientIp.resolve("10.0.2.2", "203.0.113.9, 10.0.2.3", twoHops))
    }

    @Test
    fun `a missing or unreadable header falls back to the peer`() {
        assertEquals("10.0.2.2", ClientIp.resolve("10.0.2.2", null, proxy))
        assertEquals("10.0.2.2", ClientIp.resolve("10.0.2.2", "not-an-address", proxy))
        assertEquals("10.0.2.2", ClientIp.resolve("10.0.2.2", "", proxy))
    }

    @Test
    fun `ranges and IPv6 are understood`() {
        val ranges = ClientIp.parseTrusted("172.16.0.0/12,::1/128")
        assertEquals("203.0.113.9", ClientIp.resolve("172.20.1.1", "203.0.113.9", ranges))
        assertEquals("2001:db8::7", ClientIp.resolve("0:0:0:0:0:0:0:1", "2001:db8::7", ranges))
        assertEquals("172.32.0.1", ClientIp.resolve("172.32.0.1", "203.0.113.9", ranges))
    }
}
