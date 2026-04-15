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
    fun mainLayoutUsesStatusFirstShellAndAdvancedMenu() {
        val layout = readProjectFile("app/src/main/res/layout/activity_main.xml")

        assertTrue("activity_main should expose runtime surface root", layout.contains("@+id/runtimeSurface"))
        assertTrue("activity_main should expose advanced menu button", layout.contains("@+id/advancedMenuButton"))
        assertTrue("activity_main should expose service state value", layout.contains("@+id/serviceStateValue"))
        assertTrue("activity_main should expose service status hint", layout.contains("@+id/serviceStatusHint"))
        assertTrue("activity_main should expose centered peer suffix", layout.contains("@+id/peerSuffixValue"))
        assertTrue("activity_main should keep peer suffix single-line", layout.contains("android:maxLines=\"1\""))
        assertTrue("activity_main should enable peer suffix autosize", layout.contains("android:autoSizeTextType=\"uniform\""))
        assertFalse("activity_main should remove inline runtime toggle button", layout.contains("@+id/serviceToggleButton"))
        assertFalse("activity_main should remove inline screen capture button", layout.contains("@+id/screenCapturePermissionButton"))
        assertTrue("activity_main should keep version in the shell", layout.contains("@+id/surfaceVersion"))
    }

    @Test
    fun advancedMenuResourceExposesExpectedActions() {
        val menu = readProjectFile("app/src/main/res/menu/main_actions.xml")

        assertTrue("advanced menu should expose bootstrap edit action", menu.contains("@+id/action_edit_bootstrap"))
        assertTrue("advanced menu should expose bootstrap scan action", menu.contains("@+id/action_scan_bootstrap"))
        assertTrue("advanced menu should expose screen capture action", menu.contains("@+id/action_request_screen_capture"))
        assertTrue("advanced menu should expose runtime reconnect action", menu.contains("@+id/action_reconnect_runtime"))
        assertTrue("advanced menu should use bootstrap edit string", menu.contains("@string/menu_edit_bootstrap"))
        assertTrue("advanced menu should use bootstrap scan string", menu.contains("@string/menu_scan_bootstrap"))
        assertTrue("advanced menu should use screen capture string", menu.contains("@string/menu_request_screen_capture"))
        assertTrue("advanced menu should use reconnect string", menu.contains("@string/menu_reconnect_runtime"))
    }

    @Test
    fun bootstrapInputDialogUsesSharedTextFieldShell() {
        val dialog = readProjectFile("app/src/main/res/layout/dialog_bootstrap_input.xml")

        assertTrue("bootstrap dialog should use TextInputLayout", dialog.contains("TextInputLayout"))
        assertTrue("bootstrap dialog should expose bootstrap input field", dialog.contains("@+id/bootstrapInputValue"))
        assertTrue("bootstrap dialog should hint bootstrap input", dialog.contains("@string/bootstrap_input_hint"))
        assertTrue("bootstrap dialog should accept URI text", dialog.contains("android:inputType=\"textUri\""))
    }

    @Test
    fun stringsExposeStatusFirstShellCopy() {
        val strings = readProjectFile("app/src/main/res/values/strings.xml")
        val expectedNames = listOf(
            "advanced_menu_action",
            "service_state_unconfigured",
            "service_state_connecting",
            "service_state_ready",
            "service_state_error",
            "service_hint_unconfigured",
            "service_hint_connecting",
            "service_hint_ready",
            "service_hint_error",
            "menu_edit_bootstrap",
            "menu_scan_bootstrap",
            "menu_request_screen_capture",
            "menu_reconnect_runtime",
            "bootstrap_input_hint",
            "bootstrap_save_action",
            "bootstrap_invalid_message",
            "bootstrap_required_message",
            "bootstrap_scan_invalid_message",
        )

        expectedNames.forEach { name ->
            assertTrue("strings.xml should contain $name", strings.contains("name=\"$name\""))
        }
        assertEquals("strings.xml should keep exactly one advanced menu label", 1, Regex("name=\"advanced_menu_action\"").findAll(strings).count())
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
}
