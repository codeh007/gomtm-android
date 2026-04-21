package com.gomtm.swarm.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionInputParserTest {
    @Test
    fun parsesRawConnectionAddress() {
        val result = ConnectionInputParser.parse("/ip4/156.233.234.137/udp/8443/quic-v1/p2p/12D3KooWTest")

        assertEquals("/ip4/156.233.234.137/udp/8443/quic-v1/p2p/12D3KooWTest", result.connectionAddress)
        assertNull(result.errorMessage)
    }

    @Test
    fun parsesLegacyBootstrapDeepLinkIntoConnectionAddress() {
        val result = ConnectionInputParser.parse(
            "gomtm://bootstrap?bootstrap=%2Fip4%2F156.233.234.137%2Fudp%2F8443%2Fquic-v1%2Fp2p%2F12D3KooWTest",
        )

        assertEquals("/ip4/156.233.234.137/udp/8443/quic-v1/p2p/12D3KooWTest", result.connectionAddress)
        assertNull(result.errorMessage)
    }

    @Test
    fun parsesConnectionDeepLinkWithExtraQueryParameters() {
        val result = ConnectionInputParser.parse(
            "gomtm://bootstrap?connection=%2Fip4%2F156.233.234.137%2Fudp%2F8443%2Fquic-v1%2Fp2p%2F12D3KooWTest&source=qr",
        )

        assertEquals("/ip4/156.233.234.137/udp/8443/quic-v1/p2p/12D3KooWTest", result.connectionAddress)
        assertNull(result.errorMessage)
    }

    @Test
    fun rejectsBlankInput() {
        val result = ConnectionInputParser.parse("   ")

        assertEquals("地址不能为空", result.errorMessage)
        assertNull(result.connectionAddress)
    }

    @Test
    fun rejectsUnsupportedScheme() {
        val result = ConnectionInputParser.parse("https://example.com/bootstrap")

        assertEquals("格式无效", result.errorMessage)
        assertNull(result.connectionAddress)
    }

    @Test
    fun rejectsDeepLinkWithoutConnectionQueryParameter() {
        val result = ConnectionInputParser.parse("gomtm://bootstrap?source=qr")

        assertEquals("地址不能为空", result.errorMessage)
        assertNull(result.connectionAddress)
    }

    @Test
    fun rejectsDeepLinkWithInvalidPercentEncoding() {
        val result = ConnectionInputParser.parse("gomtm://bootstrap?connection=%ZZ")

        assertEquals("格式无效", result.errorMessage)
        assertNull(result.connectionAddress)
    }
}
