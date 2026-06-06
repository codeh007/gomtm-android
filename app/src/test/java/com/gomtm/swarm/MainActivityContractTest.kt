package com.gomtm.swarm

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityContractTest {
    @Test
    fun mainLayoutUsesWebViewHostShell() {
        val layout = readProjectFile("app/src/main/res/layout/activity_main.xml")

        assertTrue("activity_main should expose a WebView host", layout.contains("@+id/p2pWebView"))
        assertTrue("activity_main should expose a minimal web error surface", layout.contains("@+id/webErrorMessage"))
        assertFalse("activity_main should remove centered peer suffix shell UI", layout.contains("@+id/peerSuffixValue"))
        assertFalse("activity_main should remove native service state shell UI", layout.contains("@+id/serviceStateValue"))
        assertFalse("activity_main should remove native advanced menu shell UI", layout.contains("@+id/advancedMenuButton"))
    }

    @Test
    fun mainActivityRegistersJavascriptBridgeAndLoadsDevicesEntry() {
        val source = readProjectFile("app/src/main/java/com/gomtm/swarm/MainActivity.kt")

        assertTrue("MainActivity should register a JS bridge", source.contains("addJavascriptInterface("))
        assertTrue("MainActivity should host GomtmWebViewBridge", source.contains("GomtmWebViewBridge"))
        assertTrue("MainActivity should load the /dash/devices entry URL from BuildConfig", source.contains("BuildConfig.GOMTM_UI_DEVICES_URL"))
        assertTrue("MainActivity should host a WebView", source.contains("WebView"))
        assertTrue("MainActivity should delegate startup to GomtmHostActions.ensureRuntimeStarted", source.contains("GomtmHostActions.ensureRuntimeStarted"))
        assertFalse("MainActivity should not keep startDeviceService helper", source.contains("private fun startDeviceService()"))
        assertFalse("MainActivity should not keep stopDeviceService helper", source.contains("private fun stopDeviceService()"))
        assertFalse("MainActivity should not auto start runtime on create", source.contains("requestRuntimeStartIfNeeded("))
        assertFalse("MainActivity should not keep runtime restart helper", source.contains("requestRuntimeRestart("))
        assertFalse("MainActivity should not import the foreground service directly", source.contains("GomtmForegroundService"))
        assertFalse("MainActivity should remove the old advanced menu flow", source.contains("showAdvancedMenu("))
        assertFalse("MainActivity should remove the old native connection dialog flow", source.contains("showConnectionDialog("))
    }

    @Test
    fun mainActivitySupportsDedicatedScreenCaptureAction() {
        val source = readProjectFile("app/src/main/java/com/gomtm/swarm/MainActivity.kt")

        assertTrue("MainActivity should define a dedicated screen capture action", source.contains("ACTION_REQUEST_SCREEN_CAPTURE"))
        assertTrue("MainActivity should handle the dedicated screen capture action", source.contains("intent?.action == ACTION_REQUEST_SCREEN_CAPTURE"))
        assertTrue("MainActivity should route the action to requestScreenCapturePermission", source.contains("requestScreenCapturePermission()"))
        assertFalse("MainActivity should not keep ACTION_START_DEVICE_SERVICE", source.contains("ACTION_START_DEVICE_SERVICE"))
    }

    @Test
    fun mainActivityDoesNotRestrictWebViewNavigationToDashP2PFamily() {
        val source = readProjectFile("app/src/main/java/com/gomtm/swarm/MainActivity.kt")

        assertFalse("MainActivity should not keep a bridged URL allowlist helper", source.contains("private fun isAllowedDashP2PUrl("))
        assertFalse("MainActivity should not intercept top-level navigations for path restriction", source.contains("override fun shouldOverrideUrlLoading("))
        assertFalse("MainActivity should not re-route unexpected navigations to an external browser", source.contains("openInExternalBrowser("))
    }

    @Test
    fun buildScriptDefinesDevicesEntryUrl() {
        val buildScript = readProjectFile("app/build.gradle.kts")

        assertTrue("build script should declare a devices build config field", buildScript.contains("GOMTM_UI_DEVICES_URL"))
        assertTrue("build script should point the host shell at /dash/devices", buildScript.contains("/dash/devices"))
    }

    @Test
    fun stringsExposeWebHostShellCopy() {
        val strings = readProjectFile("app/src/main/res/values/strings.xml")
        val expectedNames = listOf(
            "web_loading_message",
            "web_error_message",
            "web_error_reason_format",
            "screen_capture_permission_action",
        )
        val removedNames = listOf(
            "advanced_menu_action",
            "service_state_unconfigured",
            "service_state_connecting",
            "service_state_ready",
            "service_hint_unconfigured",
            "service_hint_connecting",
            "service_hint_ready",
            "service_hint_error",
            "menu_edit_connection",
            "menu_request_screen_capture",
            "menu_reconnect_runtime",
            "connection_input_hint",
            "connection_save_action",
            "connection_invalid_message",
            "connection_required_message",
            "surface_version_format",
            "peer_suffix_placeholder",
            "peer_suffix_unavailable",
            "web_navigation_blocked_message",
        )

        expectedNames.forEach { name ->
            assertTrue("strings.xml should contain $name", strings.contains("name=\"$name\""))
        }
        removedNames.forEach { name ->
            assertFalse("strings.xml should remove stale native-shell string $name", strings.contains("name=\"$name\""))
        }
        assertEquals("strings.xml should keep exactly one web error string", 1, Regex("name=\"web_error_message\"").findAll(strings).count())
    }

    @Test
    fun oldNativeDashboardResourcesAreRemoved() {
        assertFalse("native dashboard menu resource should be deleted", projectPathExists("app/src/main/res/menu/main_actions.xml"))
        assertFalse("native dashboard connection dialog resource should be deleted", projectPathExists("app/src/main/res/layout/dialog_connection_input.xml"))
        assertFalse("native dashboard running badge drawable should be deleted", projectPathExists("app/src/main/res/drawable/bg_status_running.xml"))
        assertFalse("native dashboard starting badge drawable should be deleted", projectPathExists("app/src/main/res/drawable/bg_status_starting.xml"))
        assertFalse("native dashboard stopped badge drawable should be deleted", projectPathExists("app/src/main/res/drawable/bg_status_stopped.xml"))
        assertFalse("native dashboard online icon should be deleted", projectPathExists("app/src/main/res/drawable/ic_node_online.xml"))
        assertFalse("native dashboard offline icon should be deleted", projectPathExists("app/src/main/res/drawable/ic_node_offline.xml"))
        assertFalse("native dashboard starting icon should be deleted", projectPathExists("app/src/main/res/drawable/ic_node_starting.xml"))
        assertFalse("native dashboard screen-share icon should be deleted", projectPathExists("app/src/main/res/drawable/ic_screen_share.xml"))
    }

    private fun readProjectFile(relative: String): String {
        val path = resolveProjectPath(relative)
        return String(Files.readAllBytes(path))
    }

    private fun resolveProjectPath(relative: String): Path {
        val candidates = listOf(
            Paths.get(relative),
            Paths.get("../$relative"),
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("path not found: $relative")
    }

    private fun projectPathExists(relative: String): Boolean {
        return listOf(
            Paths.get(relative),
            Paths.get("../$relative"),
        ).any(Files::exists)
    }
}
