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
    fun mainActivityRegistersJavascriptBridgeAndLoadsDashP2PEntry() {
        val source = readProjectFile("app/src/main/java/com/gomtm/swarm/MainActivity.kt")

        assertTrue("MainActivity should register a JS bridge", source.contains("addJavascriptInterface("))
        assertTrue("MainActivity should host GomtmWebViewBridge", source.contains("GomtmWebViewBridge"))
        assertTrue("MainActivity should load the /dash/p2p entry URL from BuildConfig", source.contains("BuildConfig.GOMTM_UI_DASH_P2P_URL"))
        assertTrue("MainActivity should host a WebView", source.contains("WebView"))
        assertFalse("MainActivity should remove the old advanced menu flow", source.contains("showAdvancedMenu("))
        assertFalse("MainActivity should remove the old native connection dialog flow", source.contains("showConnectionDialog("))
    }

    @Test
    fun mainActivitySupportsDedicatedScreenCaptureAction() {
        val source = readProjectFile("app/src/main/java/com/gomtm/swarm/MainActivity.kt")

        assertTrue("MainActivity should define a dedicated screen capture action", source.contains("ACTION_REQUEST_SCREEN_CAPTURE"))
        assertTrue("MainActivity should handle the dedicated screen capture action", source.contains("intent?.action == ACTION_REQUEST_SCREEN_CAPTURE"))
        assertTrue("MainActivity should route the action to requestScreenCapturePermission", source.contains("requestScreenCapturePermission()"))
    }

    @Test
    fun mainActivityRestrictsWebViewNavigationToDashP2PFamily() {
        val source = readProjectFile("app/src/main/java/com/gomtm/swarm/MainActivity.kt")

        assertTrue("MainActivity should define an allowlist helper for bridged URLs", source.contains("private fun isAllowedDashP2PUrl("))
        assertTrue("MainActivity should gate main-frame navigations", source.contains("override fun shouldOverrideUrlLoading("))
        assertTrue("MainActivity should check whether a URL stays inside the allowlist", source.contains("isAllowedDashP2PUrl(request.url)"))
        assertTrue("MainActivity should route unexpected navigations out of the bridged WebView", source.contains("openInExternalBrowser(request.url)"))
        assertTrue("MainActivity should compare against the configured /dash/p2p entry URL", source.contains("BuildConfig.GOMTM_UI_DASH_P2P_URL"))
        assertTrue("MainActivity should constrain path navigation to the /dash/p2p family", source.contains("candidatePath.startsWith(\"${'$'}allowedPath/\")"))
    }

    @Test
    fun buildScriptDefinesDashP2PEntryUrl() {
        val buildScript = readProjectFile("app/build.gradle.kts")

        assertTrue("build script should declare a dash p2p build config field", buildScript.contains("GOMTM_UI_DASH_P2P_URL"))
        assertTrue("build script should point the host shell at /dash/p2p", buildScript.contains("/dash/p2p"))
    }

    @Test
    fun connectionParserDoesNotKeepIntentCompatibilityEntry() {
        val source = readProjectFile("app/src/main/java/com/gomtm/swarm/shell/ConnectionInputParser.kt")

        assertFalse("connection parser should not keep parseIntent compat entry", source.contains("fun parseIntent("))
        assertFalse("connection parser should not read intent extras", source.contains("getStringExtra("))
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
            "surface_version_format",
            "peer_suffix_placeholder",
            "peer_suffix_unavailable",
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

    @Test
    fun readmeDescribesWebViewHostShellTruth() {
        val readme = readProjectFile("README.md")

        assertTrue("README should describe the Android app as a WebView host shell", readme.contains("WebView host shell"))
        assertTrue("README should point the shell at /dash/p2p", readme.contains("/dash/p2p"))
        assertFalse("README should not claim there is no embedded WebView", readme.contains("There is no embedded `WebView`"))
        assertFalse("README should not describe the old compact native runtime surface as the product UI", readme.contains("one compact native runtime surface in `activity_main.xml`"))
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
