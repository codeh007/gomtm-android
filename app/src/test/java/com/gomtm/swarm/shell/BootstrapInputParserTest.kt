package com.gomtm.swarm.shell

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BootstrapInputParserTest {
    @Test
    fun parsesRawBootstrapAddress() {
        val result = BootstrapInputParser.parse("/ip4/156.233.234.137/udp/8443/quic-v1/p2p/12D3KooWTest")

        assertEquals("/ip4/156.233.234.137/udp/8443/quic-v1/p2p/12D3KooWTest", result.bootstrapAddress)
        assertNull(result.errorMessage)
    }

    @Test
    fun parsesBootstrapDeepLink() {
        val result = BootstrapInputParser.parse(
            "gomtm://bootstrap?bootstrap=%2Fip4%2F156.233.234.137%2Fudp%2F8443%2Fquic-v1%2Fp2p%2F12D3KooWTest",
        )

        assertEquals("/ip4/156.233.234.137/udp/8443/quic-v1/p2p/12D3KooWTest", result.bootstrapAddress)
        assertNull(result.errorMessage)
    }

    @Test
    fun parsesBootstrapDeepLinkWithExtraQueryParameters() {
        val result = BootstrapInputParser.parse(
            "gomtm://bootstrap?bootstrap=%2Fip4%2F156.233.234.137%2Fudp%2F8443%2Fquic-v1%2Fp2p%2F12D3KooWTest&source=qr",
        )

        assertEquals("/ip4/156.233.234.137/udp/8443/quic-v1/p2p/12D3KooWTest", result.bootstrapAddress)
        assertNull(result.errorMessage)
    }

    @Test
    fun parseIntentPrefersExplicitExtra() {
        val intent = FakeIntent(
            bootstrapExtra = "/ip4/1.1.1.1/tcp/4101/p2p/extra",
            deepLink = "gomtm://bootstrap?bootstrap=%2Fip4%2F2.2.2.2%2Ftcp%2F4101%2Fp2p%2Fdeep",
        )

        val result = BootstrapInputParser.parseIntent(intent)

        assertEquals("/ip4/1.1.1.1/tcp/4101/p2p/extra", result.bootstrapAddress)
    }

    @Test
    fun parseIntentFallsBackToDeepLinkWhenExtraIsBlank() {
        val intent = FakeIntent(
            bootstrapExtra = "   ",
            deepLink = "gomtm://bootstrap?bootstrap=%2Fip4%2F2.2.2.2%2Ftcp%2F4101%2Fp2p%2Fdeep",
        )

        val result = BootstrapInputParser.parseIntent(intent)

        assertEquals("/ip4/2.2.2.2/tcp/4101/p2p/deep", result.bootstrapAddress)
        assertNull(result.errorMessage)
    }

    @Test
    fun rejectsBlankInput() {
        val result = BootstrapInputParser.parse("   ")

        assertEquals("地址不能为空", result.errorMessage)
        assertNull(result.bootstrapAddress)
    }

    @Test
    fun rejectsUnsupportedScheme() {
        val result = BootstrapInputParser.parse("https://example.com/bootstrap")

        assertEquals("格式无效", result.errorMessage)
        assertNull(result.bootstrapAddress)
    }

    @Test
    fun rejectsDeepLinkWithoutBootstrapQueryParameter() {
        val result = BootstrapInputParser.parse("gomtm://bootstrap?source=qr")

        assertEquals("地址不能为空", result.errorMessage)
        assertNull(result.bootstrapAddress)
    }

    @Test
    fun rejectsDeepLinkWithInvalidPercentEncoding() {
        val result = BootstrapInputParser.parse("gomtm://bootstrap?bootstrap=%ZZ")

        assertEquals("格式无效", result.errorMessage)
        assertNull(result.bootstrapAddress)
    }

    private class FakeIntent(
        private val bootstrapExtra: String? = null,
        private val deepLink: String? = null,
    ) : Intent() {
        override fun getStringExtra(name: String): String? {
            if (name == "bootstrap") {
                return bootstrapExtra
            }
            return null
        }

        override fun getDataString(): String? = deepLink
    }
}
