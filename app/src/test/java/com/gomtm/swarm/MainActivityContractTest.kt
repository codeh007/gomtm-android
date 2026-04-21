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

        assertTrue("advanced menu should expose connection edit action", menu.contains("@+id/action_edit_connection"))
        assertTrue("advanced menu should expose connection scan action", menu.contains("@+id/action_scan_connection"))
        assertTrue("advanced menu should expose screen capture action", menu.contains("@+id/action_request_screen_capture"))
        assertTrue("advanced menu should expose runtime reconnect action", menu.contains("@+id/action_reconnect_runtime"))
        assertTrue("advanced menu should use connection edit string", menu.contains("@string/menu_edit_connection"))
        assertTrue("advanced menu should use connection scan string", menu.contains("@string/menu_scan_connection"))
        assertTrue("advanced menu should use screen capture string", menu.contains("@string/menu_request_screen_capture"))
        assertTrue("advanced menu should use reconnect string", menu.contains("@string/menu_reconnect_runtime"))
    }

    @Test
    fun connectionInputDialogUsesSharedTextFieldShell() {
        val dialog = readProjectFile("app/src/main/res/layout/dialog_connection_input.xml")

        assertTrue("connection dialog should use TextInputLayout", dialog.contains("TextInputLayout"))
        assertTrue("connection dialog should expose connection input field", dialog.contains("@+id/connectionInputValue"))
        assertTrue("connection dialog should hint connection input", dialog.contains("@string/connection_input_hint"))
        assertTrue("connection dialog should accept URI text", dialog.contains("android:inputType=\"textUri\""))
    }

    @Test
    fun connectionParserDoesNotKeepIntentCompatibilityEntry() {
        val source = readProjectFile("app/src/main/java/com/gomtm/swarm/shell/ConnectionInputParser.kt")

        assertFalse("connection parser should not keep parseIntent compat entry", source.contains("fun parseIntent("))
        assertFalse("connection parser should not read intent extras", source.contains("getStringExtra("))
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
            "menu_edit_connection",
            "menu_scan_connection",
            "menu_request_screen_capture",
            "menu_reconnect_runtime",
            "connection_input_hint",
            "connection_save_action",
            "connection_invalid_message",
            "connection_required_message",
            "connection_scan_invalid_message",
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
