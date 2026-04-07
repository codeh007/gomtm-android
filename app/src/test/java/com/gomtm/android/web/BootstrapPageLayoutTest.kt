package com.gomtm.swarm.web

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapPageLayoutTest {
    @Test
    fun keepsPrimaryRuntimeStatusAboveConfigurationInputs() {
        val html = String(Files.readAllBytes(resolveBootstrapAssetPath("index.html")))
        val bootstrapInputIndex = html.indexOf("id=\"bootstrapInput\"")

        assertTrue("bootstrapInput anchor should exist", bootstrapInputIndex >= 0)
        assertTrue(
            "runtime state should appear before configuration form",
            html.indexOf("id=\"stateValue\"") in 0 until bootstrapInputIndex,
        )
        assertTrue(
            "peer id should appear before configuration form",
            html.indexOf("id=\"peerValue\"") in 0 until bootstrapInputIndex,
        )
        assertTrue(
            "bootstrap summary should appear before configuration form",
            html.indexOf("id=\"bootstrapValue\"") in 0 until bootstrapInputIndex,
        )
    }

    @Test
    fun replacesVerboseHeroCopyWithCompactFeatureChipsAndIconActions() {
        val html = String(Files.readAllBytes(resolveBootstrapAssetPath("index.html")))

        assertFalse("hero should no longer contain long lead paragraph", html.contains("class=\"lead\""))
        assertFalse("hero should no longer contain long hint paragraph", html.contains("class=\"hint\""))
        assertTrue("hero should expose compact feature chips", html.contains("class=\"hero-points\""))
        assertTrue("primary actions should expose icon affordances", html.contains("class=\"button-icon\""))
    }

    private fun resolveBootstrapAssetPath(fileName: String): Path {
        val candidates = listOf(
            Paths.get("app/src/main/assets/bootstrap/$fileName"),
            Paths.get("src/main/assets/bootstrap/$fileName"),
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("bootstrap asset not found: $fileName")
    }
}
