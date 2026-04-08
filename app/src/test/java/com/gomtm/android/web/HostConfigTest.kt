package com.gomtm.swarm.web

import com.gomtm.swarm.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostConfigTest {
    @Test
    fun usesSwarmApplicationId() {
        assertEquals("com.gomtm.swarm", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun resolvesHostNavigationUrlAndAppendsDashP2PWhenOnlyOriginIsProvided() {
        assertEquals("https://gomtm.example.com/dash/p2p", resolveHostNavigationUrl("gomtm.example.com"))
        assertEquals("http://10.0.2.2:3700/dash/p2p", resolveHostNavigationUrl("http://10.0.2.2:3700"))
        assertEquals(
            "https://gomtm.example.com/dash/p2p?tab=host",
            resolveHostNavigationUrl("https://gomtm.example.com?tab=host"),
        )
    }

    @Test
    fun resolvesEmbeddedConsoleUrlAsSecureLocalHostWhileKeepingRemotePath() {
        assertEquals(
            "https://gomtm.console.invalid/dash/p2p",
            resolveEmbeddedConsoleUrl("http://156.233.234.137:3700/dash/p2p"),
        )
        assertEquals(
            "https://gomtm.console.invalid/gomtm/dash/p2p?tab=host",
            resolveEmbeddedConsoleUrl("https://gomtm.example.com/gomtm/dash/p2p?tab=host"),
        )
    }

    @Test
    fun rejectsEmbeddedConsoleUrlAsCanonicalInputAndRewritesSameOriginRedirects() {
        assertNull(resolveHostNavigationUrl("https://gomtm.console.invalid/dash/p2p"))
        assertEquals(
            "https://gomtm.console.invalid/login?next=%2Fdash%2Fp2p",
            rewriteEmbeddedConsoleLocation(
                currentConsoleUrl = "http://156.233.234.137:3700/dash/p2p",
                rawLocation = "/login?next=%2Fdash%2Fp2p",
            ),
        )
        assertEquals(
            "https://accounts.example.com/oauth/start",
            rewriteEmbeddedConsoleLocation(
                currentConsoleUrl = "https://gomtm.example.com/dash/p2p",
                rawLocation = "https://accounts.example.com/oauth/start",
            ),
        )
    }
}
