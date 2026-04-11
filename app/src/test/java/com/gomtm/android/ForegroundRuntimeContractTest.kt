package com.gomtm.swarm

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRuntimeContractTest {
    @Test
    fun manifestDeclaresForegroundRuntimeAndBootRestoreComponents() {
        val manifest = String(Files.readAllBytes(resolveProjectPath("app/src/main/AndroidManifest.xml")))

        assertTrue(
            "manifest should declare RECEIVE_BOOT_COMPLETED for unattended restart",
            manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"),
        )
        assertTrue(
            "manifest should declare a foreground runtime service",
            manifest.contains(".swarm.GomtmForegroundService"),
        )
        assertTrue(
            "manifest should declare a boot restore receiver",
            manifest.contains(".swarm.GomtmBootReceiver"),
        )
        assertTrue(
            "boot receiver should listen for BOOT_COMPLETED",
            manifest.contains("android.intent.action.BOOT_COMPLETED"),
        )
    }

    @Test
    fun foregroundRuntimeServiceKeepsBootstrapAndTickLoopOutsideActivity() {
        val source = String(
            Files.readAllBytes(
                resolveProjectPath("app/src/main/java/com/gomtm/android/swarm/GomtmForegroundService.kt"),
            ),
        )

        assertTrue(source.contains("START_STICKY"))
        assertTrue(source.contains("SwarmRuntime"))
        assertTrue(source.contains("processRemoteControlTick"))
        assertTrue(source.contains("bootstrap"))
    }

    @Test
    fun screenCaptureServiceStartsForegroundWithMediaProjectionType() {
        val source = String(
            Files.readAllBytes(
                resolveProjectPath("app/src/main/java/com/gomtm/android/swarm/ScreenCaptureService.kt"),
            ),
        )

        assertTrue(source.contains("FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION"))
        assertTrue(source.contains("val notification = buildNotification()"))
        assertTrue(source.contains("startForeground("))
        assertTrue(source.contains("return START_NOT_STICKY"))
        assertTrue(source.contains("projectionData == null"))
        assertTrue(source.contains("AndroidScreenStreamHost.startProjection(this, resultCode, projectionData)"))
        assertTrue(source.contains("stopRuntimeService()"))
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
