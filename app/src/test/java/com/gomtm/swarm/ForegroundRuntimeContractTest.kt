package com.gomtm.swarm

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRuntimeContractTest {
    @Test
    fun manifestDeclaresDeepLinkForBootstrapImport() {
        val manifest = String(Files.readAllBytes(resolveProjectPath("app/src/main/AndroidManifest.xml")))

        assertTrue(manifest.contains("android.intent.action.VIEW"))
        assertTrue(manifest.contains("android.intent.category.BROWSABLE"))
        assertTrue(manifest.contains("android:scheme=\"gomtm\""))
        assertTrue(manifest.contains("android:host=\"bootstrap\""))
    }

    @Test
    fun mainActivityHostsWebViewShellWithoutAutoStartingRuntime() {
        val source = String(
            Files.readAllBytes(resolveProjectPath("app/src/main/java/com/gomtm/swarm/MainActivity.kt")),
        )

        assertTrue(source.contains("private lateinit var webView: WebView"))
        assertTrue(source.contains("private lateinit var webErrorMessage: TextView"))
        assertTrue(source.contains("configureWebView()"))
        assertTrue(source.contains("requestScreenCapturePermission()"))
        assertFalse(source.contains("requestRuntimeStartIfNeeded("))
        assertFalse(source.contains("requestRuntimeRestart("))
        assertFalse(source.contains("GomtmForegroundService.start("))
        assertFalse(source.contains("PopupMenu"))
        assertFalse(source.contains("showConnectionDialog"))
    }

    @Test
    fun mainActivityTreatsExternalDeepLinkAsPageNavigationOnly() {
        val source = String(
            Files.readAllBytes(resolveProjectPath("app/src/main/java/com/gomtm/swarm/MainActivity.kt")),
        )

        assertFalse(source.contains("ConnectionInputParser.parseIntent(intent)"))
        assertFalse(source.contains("ConnectionInputParser.parse(deepLink)"))
        assertFalse(source.contains("NodeRuntimeConfig("))
        assertFalse(source.contains("runtimeStore.save("))
        assertFalse(source.contains("getStringExtra("))
        assertFalse(source.contains("INTERNAL_CONNECTION_EXTRA"))
    }

    @Test
    fun mainActivityUsesMinimalWebFallbackInsteadOfOldNativeStatusShell() {
        val source = String(
            Files.readAllBytes(resolveProjectPath("app/src/main/java/com/gomtm/swarm/MainActivity.kt")),
        )

        assertTrue(source.contains("webErrorMessage.visibility = View.VISIBLE"))
        assertTrue(source.contains("webView.visibility = View.GONE"))
        assertFalse(source.contains("renderServiceState("))
    }

    @Test
    fun manifestDeclaresForegroundRuntimeAndBootRestoreComponents() {
        val manifest = String(Files.readAllBytes(resolveProjectPath("app/src/main/AndroidManifest.xml")))

        assertTrue(
            "manifest should declare RECEIVE_BOOT_COMPLETED for future explicit activation policies",
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
    fun foregroundRuntimeServiceStillExistsButIsNoLongerMainActivityDriven() {
        val source = String(
            Files.readAllBytes(
                resolveProjectPath("app/src/main/java/com/gomtm/swarm/platform/lifecycle/GomtmForegroundService.kt"),
            ),
        )

        assertTrue(source.contains("START_STICKY"))
        assertTrue(source.contains("GomtmRuntimeFacade"))
        assertTrue(source.contains("processRemoteControlTick"))
        assertTrue(source.contains("connectionAddress"))
        assertTrue(source.contains("connection is blank"))
        assertFalse(source.contains("DEFAULT_BOOTSTRAP"))
    }

    @Test
    fun bootReceiverNoLongerAutoRestoresRuntimeFromPersistedConnection() {
        val source = String(
            Files.readAllBytes(
                resolveProjectPath("app/src/main/java/com/gomtm/swarm/platform/lifecycle/GomtmBootReceiver.kt"),
            ),
        )

        assertTrue(source.contains("Intent.ACTION_BOOT_COMPLETED"))
        assertTrue(source.contains("Intent.ACTION_MY_PACKAGE_REPLACED"))
        assertFalse(source.contains("NodeRuntimeStore("))
        assertFalse(source.contains("GomtmForegroundService.start("))
        assertFalse(source.contains("connectionAddress"))
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
        assertTrue(buildScript.contains("GOMTM_UI_DASH_P2P_URL"))
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
