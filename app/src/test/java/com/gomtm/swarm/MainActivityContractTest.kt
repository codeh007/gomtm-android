package com.gomtm.swarm

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityContractTest {
    @Test
    fun mainLayoutUsesCompactRuntimeSurfaceInsteadOfWebViewHost() {
        val layout = String(Files.readAllBytes(resolveProjectPath("app/src/main/res/layout/activity_main.xml")))

        assertFalse("activity_main should no longer be a WebView host", layout.contains("hostWebView"))
        assertFalse("activity_main should no longer declare a root WebView", layout.contains("<WebView"))
        assertFalse("activity_main should not keep redundant runtime status pill", layout.contains("@+id/serviceStatusValue"))
        assertFalse("activity_main should not keep redundant runtime detail text", layout.contains("@+id/serviceStatusDetail"))
        assertFalse("activity_main should not keep title copy", layout.contains("@+id/surfaceTitle"))
        assertFalse("activity_main should not keep subtitle copy", layout.contains("surface_subtitle"))
        assertTrue("activity_main should expose runtime surface root", layout.contains("@+id/runtimeSurface"))
        assertTrue("activity_main should expose centered peer suffix", layout.contains("@+id/peerSuffixValue"))
        assertTrue("activity_main should expose single toggle button", layout.contains("@+id/serviceToggleButton"))
        assertTrue("activity_main should expose screen capture permission button", layout.contains("@+id/screenCapturePermissionButton"))
        assertTrue("activity_main should keep version in the shell", layout.contains("@+id/surfaceVersion"))
    }

    @Test
    fun mainActivityDropsEmbeddedConsoleBridge() {
        val source = String(Files.readAllBytes(resolveProjectPath("app/src/main/java/com/gomtm/swarm/MainActivity.kt")))

        assertFalse("MainActivity should no longer import WebView", source.contains("android.webkit.WebView"))
        assertFalse("MainActivity should no longer depend on GomtmHostBridge", source.contains("GomtmHostBridge"))
        assertFalse("MainActivity should no longer depend on HostSettingsStore", source.contains("HostSettingsStore"))
        assertTrue("MainActivity should still render runtime state", source.contains("renderServiceState"))
        assertTrue("MainActivity should still support runtime toggling", source.contains("toggleService"))
        assertFalse("MainActivity should not expose an auto_start launch contract anymore", source.contains("EXTRA_AUTO_START"))
        assertFalse("MainActivity should no longer read auto_start from intent", source.contains("getBooleanExtra("))
        assertTrue("MainActivity should consume runtime facade instead of old swarm runtime class", source.contains("GomtmRuntimeFacade"))
        assertTrue("MainActivity should delegate runtime ownership to foreground service", source.contains("GomtmForegroundService"))
        assertTrue("MainActivity should request MediaProjection permission", source.contains("MediaProjectionManager"))
        assertTrue("MainActivity should start ScreenCaptureService after permission grant", source.contains("ScreenCaptureService.startProjection"))
        assertTrue("MainActivity should animate shell background between states", source.contains("animateSurfaceBackground"))
        assertTrue("MainActivity should project peer suffix into the center shell", source.contains("peerSuffixForSnapshot"))
        assertTrue("MainActivity should keep placeholder out of accessibility copy", source.contains("semanticPeerSuffixForSnapshot"))
        assertTrue("MainActivity should use locale-stable peer suffix casing", source.contains("Locale.ROOT"))
        assertFalse("MainActivity should not keep a default bootstrap fallback", source.contains("DEFAULT_BOOTSTRAP"))
        assertTrue("MainActivity should refuse blank bootstrap starts", source.contains("bootstrap is blank"))
    }

    private fun resolveProjectPath(relative: String): Path {
        val candidates = listOf(
            Paths.get(relative),
            Paths.get("../$relative"),
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("path not found: $relative")
    }
}
