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
    fun removesPseudoHeroActionsAndKeepsSingleActionSet() {
        val html = String(Files.readAllBytes(resolveBootstrapAssetPath("index.html")))

        assertFalse("hero should no longer contain long lead paragraph", html.contains("class=\"lead\""))
        assertFalse("hero should no longer contain long hint paragraph", html.contains("class=\"hint\""))
        assertFalse("hero should no longer expose pseudo action chips", html.contains("class=\"hero-points\""))
        assertFalse("hero should no longer expose decorative eyebrow copy", html.contains("class=\"eyebrow\""))
        assertFalse("hero should no longer mention fake connect action", html.contains("接入节点"))
        assertFalse("hero should no longer mention fake permission action", html.contains("权限设置"))
        assertTrue("real accessibility action should remain", html.contains("id=\"openAccessibilityButton\""))
        assertTrue("real screen capture action should remain", html.contains("id=\"requestScreenCaptureButton\""))
        assertTrue("real console action should remain", html.contains("id=\"openConsoleButton\""))
        assertTrue("primary actions should expose icon affordances", html.contains("class=\"button-icon\""))
    }

    @Test
    fun groupsRealActionsByRuntimePermissionsAndConsole() {
        val html = String(Files.readAllBytes(resolveBootstrapAssetPath("index.html")))

        val runtimeGroupIndex = html.indexOf("id=\"runtimeActionGroup\"")
        val permissionGroupIndex = html.indexOf("id=\"permissionActionGroup\"")
        val consoleGroupIndex = html.indexOf("id=\"consoleActionGroup\"")

        assertTrue("runtime action group should exist", runtimeGroupIndex >= 0)
        assertTrue("permission action group should exist", permissionGroupIndex >= 0)
        assertTrue("console action group should exist", consoleGroupIndex >= 0)
        assertTrue("runtime group should appear before permission group", runtimeGroupIndex < permissionGroupIndex)
        assertTrue("permission group should appear before console group", permissionGroupIndex < consoleGroupIndex)
        assertTrue(
            "start button should belong to runtime group",
            html.indexOf("id=\"startButton\"") in runtimeGroupIndex until permissionGroupIndex,
        )
        assertTrue(
            "stop button should belong to runtime group",
            html.indexOf("id=\"stopButton\"") in runtimeGroupIndex until permissionGroupIndex,
        )
        assertTrue(
            "accessibility button should belong to permission group",
            html.indexOf("id=\"openAccessibilityButton\"") in permissionGroupIndex until consoleGroupIndex,
        )
        assertTrue(
            "screen capture button should belong to permission group",
            html.indexOf("id=\"requestScreenCaptureButton\"") in permissionGroupIndex until consoleGroupIndex,
        )
        assertTrue(
            "console button should belong to console group",
            html.indexOf("id=\"openConsoleButton\"") > consoleGroupIndex,
        )
    }

    @Test
    fun movesSaveActionBelowConfigFieldsAndAddsDirtyStatusAnchor() {
        val html = String(Files.readAllBytes(resolveBootstrapAssetPath("index.html")))

        val consoleUrlIndex = html.indexOf("id=\"consoleUrlInput\"")
        val dirtyBarIndex = html.indexOf("id=\"configDirtyBar\"")
        val actionGroupsIndex = html.indexOf("class=\"action-groups\"")

        assertTrue("console url input should exist", consoleUrlIndex >= 0)
        assertTrue("dirty status bar should exist", dirtyBarIndex >= 0)
        assertTrue("action groups should exist", actionGroupsIndex >= 0)
        assertTrue(
            "dirty status bar should appear after config fields",
            dirtyBarIndex > consoleUrlIndex,
        )
        assertTrue(
            "dirty status bar should appear before action groups",
            dirtyBarIndex < actionGroupsIndex,
        )
        assertTrue("dirty status text anchor should exist", html.contains("id=\"configDirtyText\""))
        assertTrue("save button text anchor should exist", html.contains("id=\"saveButtonText\""))
    }

    @Test
    fun exposesInlineValidationAnchorsAndSingleDiagnosticsCard() {
        val html = String(Files.readAllBytes(resolveBootstrapAssetPath("index.html")))

        assertTrue("bootstrap validation anchor should exist", html.contains("id=\"bootstrapValidationText\""))
        assertTrue("console url validation anchor should exist", html.contains("id=\"consoleUrlValidationText\""))
        assertTrue("diagnostics card should exist", html.contains("id=\"diagnosticsCard\""))
        assertTrue("diagnostics summary anchor should exist", html.contains("id=\"diagnosticsSummaryText\""))
        assertTrue("diagnostics body anchor should exist", html.contains("id=\"diagnosticsBody\""))
        assertTrue("diagnostics peers section should exist", html.contains("id=\"diagnosticsPeersSection\""))
        assertTrue("diagnostics error section should exist", html.contains("id=\"diagnosticsErrorSection\""))
        assertTrue("diagnostics logs section should exist", html.contains("id=\"diagnosticsLogsSection\""))
        assertFalse("legacy peers card heading should be removed", html.contains("<h2>已发现节点</h2>"))
        assertFalse("legacy error card heading should be removed", html.contains("<h2>错误</h2>"))
        assertFalse("legacy logs card heading should be removed", html.contains("<h2>最近日志</h2>"))
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
