package com.gomtm.swarm.platform.remote

import android.content.Context
import android.os.Build
import com.gomtm.swarm.platform.accessibility.GomtmAccessibilityService

class AndroidRemoteControlOps(
    private val context: Context,
) : RemoteControlOps {
    override fun screenSnapshot(format: String): RemoteControlCommandResult<RemoteControlScreenshotPayload> {
        if (!GomtmAccessibilityService.isEnabled(context)) {
            return RemoteControlCommandResult.Error(
                code = "SB_PERMISSION_REQUIRED",
                message = "accessibility service is not enabled",
                retryable = false,
            )
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return RemoteControlCommandResult.Error(
                code = "SB_CAPABILITY_UNAVAILABLE",
                message = "screen snapshot requires Android 11 or newer",
                retryable = false,
            )
        }
        val payload = GomtmAccessibilityService.captureRemoteControlScreenshot()
            ?: return RemoteControlCommandResult.Error(
                code = "SB_INPUT_FAILED",
                message = "failed to capture remote control screenshot",
                retryable = true,
            )
        return RemoteControlCommandResult.Success(payload)
    }

    override fun screenStreamEnsure(): RemoteControlCommandResult<RemoteControlScreenStreamPayload> {
        return AndroidScreenStreamHost.ensureStream(context)
    }

    override fun screenStreamStop(): RemoteControlCommandResult<RemoteControlActionPayload> {
        return AndroidScreenStreamHost.stopStream(context)
    }

    override fun inputTap(x: Int, y: Int): RemoteControlCommandResult<RemoteControlActionPayload> {
        return executeAction("tap") { GomtmAccessibilityService.performTap(x, y) }
    }

    override fun inputSwipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Int,
    ): RemoteControlCommandResult<RemoteControlActionPayload> {
        return executeAction("swipe") {
            GomtmAccessibilityService.performSwipe(startX, startY, endX, endY, durationMs)
        }
    }

    override fun inputText(text: String): RemoteControlCommandResult<RemoteControlActionPayload> {
        return executeAction("text") { GomtmAccessibilityService.performText(text) }
    }

    override fun inputKey(key: String): RemoteControlCommandResult<RemoteControlActionPayload> {
        return executeAction("key") { GomtmAccessibilityService.performKey(key) }
    }

    private fun executeAction(
        action: String,
        block: () -> Boolean,
    ): RemoteControlCommandResult<RemoteControlActionPayload> {
        if (!GomtmAccessibilityService.isEnabled(context)) {
            return RemoteControlCommandResult.Error(
                code = "SB_PERMISSION_REQUIRED",
                message = "accessibility service is not enabled",
                retryable = false,
            )
        }
        return if (block()) {
            RemoteControlCommandResult.Success(RemoteControlActionPayload(status = "ok"))
        } else {
            RemoteControlCommandResult.Error(
                code = "SB_INPUT_FAILED",
                message = "failed to execute remote control $action",
                retryable = true,
            )
        }
    }
}
