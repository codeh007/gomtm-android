package com.gomtm.swarm

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRuntimeContractTest {
    @Test
    fun manifestDeclaresDeepLinkAndCameraPermissionForBootstrapImport() {
        val manifest = String(Files.readAllBytes(resolveProjectPath("app/src/main/AndroidManifest.xml")))

        assertTrue(manifest.contains("android.permission.CAMERA"))
        assertTrue(manifest.contains("android.intent.action.VIEW"))
        assertTrue(manifest.contains("android.intent.category.BROWSABLE"))
        assertTrue(manifest.contains("android:scheme=\"gomtm\""))
        assertTrue(manifest.contains("android:host=\"bootstrap\""))
    }

    @Test
    fun mainActivityRoutesAdvancedMenuToBootstrapDialogAndScanner() {
        val source = String(
            Files.readAllBytes(resolveProjectPath("app/src/main/java/com/gomtm/swarm/MainActivity.kt")),
        )

        assertTrue(source.contains("ScanContract"))
        assertTrue(source.contains("ScanOptions"))
        assertTrue(source.contains("showAdvancedMenu"))
        assertTrue(source.contains("showBootstrapDialog"))
        assertTrue(source.contains("requestRuntimeRestart"))
        assertTrue(source.contains("BootstrapInputParser.parseIntent"))
        assertTrue(source.contains("R.id.action_scan_bootstrap"))
    }

    @Test
    fun manifestDeclaresForegroundRuntimeAndBootRestoreComponents() {
        val manifest = String(Files.readAllBytes(resolveProjectPath("app/src/main/AndroidManifest.xml")))

        assertTrue(
            "manifest should declare RECEIVE_BOOT_COMPLETED for unattended restart",
            manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"),
        )
        assertTrue(
            "manifest should declare a foreground runtime service",
            manifest.contains(".platform.lifecycle.GomtmForegroundService"),
        )
        assertTrue(
            "manifest should declare a boot restore receiver",
            manifest.contains(".platform.lifecycle.GomtmBootReceiver"),
        )
        assertTrue(
            "manifest should declare an accessibility service in the platform boundary",
            manifest.contains(".platform.accessibility.GomtmAccessibilityService"),
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
                resolveProjectPath("app/src/main/java/com/gomtm/swarm/platform/lifecycle/GomtmForegroundService.kt"),
            ),
        )

        assertTrue(source.contains("START_STICKY"))
        assertTrue(source.contains("GomtmRuntimeFacade"))
        assertTrue(source.contains("processRemoteControlTick"))
        assertTrue(source.contains("bootstrap"))
        assertTrue(source.contains("bootstrap is blank"))
        assertFalse(source.contains("DEFAULT_BOOTSTRAP"))
    }

    @Test
    fun bootReceiverOnlyRestoresRuntimeWhenBootstrapWasPersisted() {
        val source = String(
            Files.readAllBytes(
                resolveProjectPath("app/src/main/java/com/gomtm/swarm/platform/lifecycle/GomtmBootReceiver.kt"),
            ),
        )

        assertTrue(source.contains("config.bootstrapAddress.isBlank()"))
        assertFalse(source.contains("DEFAULT_BOOTSTRAP"))
    }

    @Test
    fun screenCaptureServiceStartsForegroundWithMediaProjectionType() {
        val source = String(
            Files.readAllBytes(
                resolveProjectPath("app/src/main/java/com/gomtm/swarm/platform/lifecycle/ScreenCaptureService.kt"),
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

    @Test
    fun releaseWorkflowProjectsTagIntoGradleVersionMetadata() {
        val buildScript = String(Files.readAllBytes(resolveProjectPath("app/build.gradle.kts")))
        val releaseWorkflow = String(Files.readAllBytes(resolveProjectPath(".github/workflows/release.yml")))

        assertTrue(buildScript.contains("gomtmReleaseTag"))
        assertTrue(buildScript.contains("versionCodeFrom(appVersionName)"))
        assertTrue(releaseWorkflow.contains("GOMTM_RELEASE_TAG="))
        assertTrue(releaseWorkflow.contains("-PgomtmReleaseTag=\"${'$'}{RELEASE_TAG}\""))
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
