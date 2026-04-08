package com.gomtm.swarm.swarm

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GomtmAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        private var instance: GomtmAccessibilityService? = null

        fun isEnabled(context: Context): Boolean {
            val expectedComponent = ComponentName(context, GomtmAccessibilityService::class.java).flattenToString()
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val enabled = enabledServices.split(':').any {
                it.equals(expectedComponent, ignoreCase = true)
            }
            return enabled && instance != null
        }

        fun performTap(x: Int, y: Int): Boolean {
            return instance?.dispatchTapAbsolute(x, y) ?: false
        }

        fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int): Boolean {
            return instance?.dispatchSwipeAbsolute(startX, startY, endX, endY, durationMs) ?: false
        }

        fun performText(text: String): Boolean {
            return instance?.setFocusedText(text) ?: false
        }

        fun performKey(key: String): Boolean {
            return instance?.performKeyInternal(key) ?: false
        }

        fun captureRemoteControlScreenshot(): RemoteControlScreenshotPayload? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return null
            }
            return instance?.captureRemoteControlScreenshotInternal()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun dispatchTapAbsolute(x: Int, y: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 120L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun dispatchSwipeAbsolute(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }
        val effectiveDuration = durationMs.coerceAtLeast(120)
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, effectiveDuration.toLong()))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun resolveActiveRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { return it }
        return windows.firstNotNullOfOrNull { it.root }
    }

    private fun setFocusedText(text: String): Boolean {
        val root = resolveActiveRoot() ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val target = when {
            focused?.isEditable == true -> focused
            else -> findEditableNode(root)
        } ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) {
            return node
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val editable = findEditableNode(child)
            if (editable != null) {
                return editable
            }
        }
        return null
    }

    private fun performKeyInternal(key: String): Boolean {
        return when (key.trim().lowercase(Locale.US)) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents", "recent_apps", "overview" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            else -> false
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureRemoteControlScreenshotInternal(): RemoteControlScreenshotPayload? {
        val payloadRef = AtomicReference<RemoteControlScreenshotPayload?>()
        val latch = CountDownLatch(1)
        takeScreenshot(
            display?.displayId ?: 0,
            ContextCompat.getMainExecutor(this),
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        val wrapped = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        if (wrapped != null) {
                            val bitmap = wrapped.copy(Bitmap.Config.ARGB_8888, false) ?: wrapped
                            val bytes = ByteArrayOutputStream().use { output ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                                output.toByteArray()
                            }
                            val payload = RemoteControlScreenshotPayload(
                                mimeType = "image/png",
                                imageBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP),
                                width = bitmap.width,
                                height = bitmap.height,
                                capturedAt = utcTimestamp(),
                            )
                            if (bitmap !== wrapped) {
                                bitmap.recycle()
                            }
                            wrapped.recycle()
                            payloadRef.set(payload)
                        }
                    } finally {
                        screenshot.hardwareBuffer.close()
                        latch.countDown()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    latch.countDown()
                }
            },
        )
        latch.await(2, TimeUnit.SECONDS)
        return payloadRef.get()
    }

    private fun utcTimestamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }
}
