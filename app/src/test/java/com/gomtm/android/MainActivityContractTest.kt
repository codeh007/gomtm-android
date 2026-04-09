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
        assertTrue("activity_main should expose runtime status value", layout.contains("@+id/serviceStatusValue"))
        assertTrue("activity_main should expose runtime status detail", layout.contains("@+id/serviceStatusDetail"))
        assertTrue("activity_main should expose single toggle button", layout.contains("@+id/serviceToggleButton"))
        assertTrue("activity_main should expose screen capture permission button", layout.contains("@+id/screenCapturePermissionButton"))
    }

    @Test
    fun mainActivityDropsEmbeddedConsoleBridge() {
        val source = String(Files.readAllBytes(resolveProjectPath("app/src/main/java/com/gomtm/android/MainActivity.kt")))

        assertFalse("MainActivity should no longer import WebView", source.contains("android.webkit.WebView"))
        assertFalse("MainActivity should no longer depend on GomtmHostBridge", source.contains("GomtmHostBridge"))
        assertFalse("MainActivity should no longer depend on HostSettingsStore", source.contains("HostSettingsStore"))
        assertTrue("MainActivity should still render runtime state", source.contains("renderServiceState"))
        assertTrue("MainActivity should still support runtime toggling", source.contains("toggleService"))
        assertFalse("MainActivity should not expose an auto_start launch contract anymore", source.contains("EXTRA_AUTO_START"))
        assertFalse("MainActivity should no longer read auto_start from intent", source.contains("getBooleanExtra("))
        assertTrue("MainActivity should delegate runtime ownership to foreground service", source.contains("GomtmForegroundService"))
        assertTrue("MainActivity should request MediaProjection permission", source.contains("MediaProjectionManager"))
        assertTrue("MainActivity should start ScreenCaptureService after permission grant", source.contains("ScreenCaptureService.startProjection"))
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
