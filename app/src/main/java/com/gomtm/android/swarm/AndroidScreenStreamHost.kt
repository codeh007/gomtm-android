package com.gomtm.swarm.swarm

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.WindowManager
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

object AndroidScreenStreamHost {
    private val lock = Any()
    private var session: ScreenStreamSession? = null

    fun currentPermissionState(context: Context): String {
        if (!isSupported()) {
            return "unsupported"
        }
        synchronized(lock) {
            return if (session?.isProjectionActive() == true) "granted" else "not_granted"
        }
    }

    fun currentCapabilityState(context: Context): RemoteControlCapabilityState {
        if (!isSupported()) {
            return RemoteControlCapabilityState(state = "unavailable", reason = "unsupported")
        }
        synchronized(lock) {
            val active = session
            if (active == null) {
                return RemoteControlCapabilityState(state = "permission_required", reason = "screen_capture_not_granted")
            }
            if (active.lastError().isNotBlank()) {
                return RemoteControlCapabilityState(state = "host_not_ready", reason = "capture_session_error")
            }
            return if (active.isStreamingReady()) {
                RemoteControlCapabilityState(state = "streaming")
            } else {
                RemoteControlCapabilityState(state = "starting")
            }
        }
    }

    fun startProjection(context: Context, resultCode: Int, data: Intent) {
        synchronized(lock) {
            session?.stop()
            session = ScreenStreamSession(context.applicationContext, resultCode, data).also { it.start() }
        }
    }

    fun ensureStream(context: Context): RemoteControlCommandResult<RemoteControlScreenStreamPayload> {
        if (!isSupported()) {
            return RemoteControlCommandResult.Error(
                code = "SB_CAPABILITY_UNAVAILABLE",
                message = "screen streaming requires MediaProjection and H.264 support",
                retryable = false,
            )
        }
        val active = synchronized(lock) { session }
            ?: return RemoteControlCommandResult.Error(
                code = "SB_PERMISSION_REQUIRED",
                message = "screen capture permission is not granted",
                retryable = false,
            )
        return active.ensureReady()
    }

    fun stopStream(context: Context): RemoteControlCommandResult<RemoteControlActionPayload> {
        synchronized(lock) {
            session?.stop()
            session = null
        }
        ScreenCaptureService.stopProjection(context.applicationContext)
        return RemoteControlCommandResult.Success(RemoteControlActionPayload(status = "stopped"))
    }

    fun onServiceDestroyed() {
        synchronized(lock) {
            session?.stop()
            session = null
        }
    }

    private fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

    private class ScreenStreamSession(
        private val context: Context,
        private val resultCode: Int,
        private val permissionData: Intent,
    ) {
        private val running = AtomicBoolean(false)
        private val projectionManager =
            context.getSystemService(MediaProjectionManager::class.java)
        private var projection: MediaProjection? = null
        private var codec: MediaCodec? = null
        private var inputSurface: android.view.Surface? = null
        private var virtualDisplay: VirtualDisplay? = null
        private var serverSocket: ServerSocket? = null
        private var serverThread: Thread? = null
        private var encodeThread: Thread? = null
        private var codecThread: HandlerThread? = null
        private var projectionCallbackRegistered = false
        private var activeClient: Socket? = null
        private var activeOutput: OutputStream? = null
        private var width = 0
        private var height = 0
        private var rotation = 0
        private var codecString = ""
        private var codecConfig: ByteArray? = null
        private var lastErrorMessage = ""

        fun start() {
            runCatching {
                running.set(true)
                val projectionValue = projectionManager.getMediaProjection(resultCode, permissionData)
                    ?: error("failed to obtain MediaProjection")
                projection = projectionValue
                val metrics = currentDisplayMetrics(context)
                width = metrics.width
                height = metrics.height
                rotation = 0
                serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
                startServerLoop()
                startEncoder(projectionValue, metrics.densityDpi)
            }.onFailure { error ->
                lastErrorMessage = error.message ?: error.javaClass.simpleName
                stop()
            }
        }

        fun isProjectionActive(): Boolean = projection != null && running.get()

        fun isStreamingReady(): Boolean {
            return running.get() && serverSocket != null && codecString.isNotBlank() && width > 0 && height > 0
        }

        fun lastError(): String = lastErrorMessage

        fun ensureReady(): RemoteControlCommandResult<RemoteControlScreenStreamPayload> {
            repeat(40) {
                if (isStreamingReady()) {
                    val socket = serverSocket
                        ?: return RemoteControlCommandResult.Error(
                            code = "SB_INPUT_FAILED",
                            message = "screen stream server socket is not ready",
                            retryable = true,
                        )
                    return RemoteControlCommandResult.Success(
                        RemoteControlScreenStreamPayload(
                            status = "streaming",
                            resolved = RemoteControlStreamResolvedTarget(
                                kind = "loopback_tcp",
                                host = "127.0.0.1",
                                port = socket.localPort,
                                protocolHint = "tcp",
                                serviceHint = "android_media_projection_h264",
                            ),
                            channel = RemoteControlStreamChannelPayload(
                                kind = "video_h264_annexb",
                                framing = "length_prefixed_access_units",
                                codec = codecString,
                                width = width,
                                height = height,
                                rotation = rotation,
                                keyframeRequiredOnStart = true,
                            ),
                            lastError = lastErrorMessage.ifBlank { null },
                        ),
                    )
                }
                if (lastErrorMessage.isNotBlank()) {
                    return RemoteControlCommandResult.Error(
                        code = "SB_INPUT_FAILED",
                        message = lastErrorMessage,
                        retryable = true,
                    )
                }
                Thread.sleep(100)
            }
            return RemoteControlCommandResult.Error(
                code = "SB_TIMEOUT",
                message = "screen stream is still starting",
                retryable = true,
            )
        }

        fun stop() {
            running.set(false)
            closeClient()
            runCatching { serverSocket?.close() }
            runCatching { virtualDisplay?.release() }
            runCatching { inputSurface?.release() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (projectionCallbackRegistered) {
                runCatching { projection?.unregisterCallback(projectionCallback) }
            }
            runCatching { projection?.stop() }
            runCatching { codecThread?.quitSafely() }
            serverThread = null
            encodeThread = null
            serverSocket = null
            virtualDisplay = null
            inputSurface = null
            codec = null
            projection = null
            codecThread = null
            codecConfig = null
            codecString = ""
        }

        private fun startServerLoop() {
            val socket = serverSocket ?: return
            serverThread = Thread {
                while (running.get()) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    synchronized(this) {
                        closeClient()
                        activeClient = client
                        activeOutput = client.getOutputStream()
                        requestSyncFrame()
                    }
                }
            }.also {
                it.name = "gomtm-screen-stream-accept"
                it.isDaemon = true
                it.start()
            }
        }

        private fun startEncoder(projectionValue: MediaProjection, densityDpi: Int) {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, max(width * height * 4, 2_000_000))
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codecThread = HandlerThread("gomtm-screen-stream-codec").also { it.start() }
            val handler = Handler(codecThread!!.looper)
            projectionValue.registerCallback(projectionCallback, handler)
            projectionCallbackRegistered = true
            val codecValue = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codecValue.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codecValue.createInputSurface()
            codecValue.start()
            codec = codecValue
            virtualDisplay = projectionValue.createVirtualDisplay(
                "gomtm-screen-stream",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                handler,
            )
            startEncodeLoop(codecValue)
        }

        private fun startEncodeLoop(codecValue: MediaCodec) {
            encodeThread = Thread {
                val bufferInfo = MediaCodec.BufferInfo()
                while (running.get()) {
                    when (val outputIndex = codecValue.dequeueOutputBuffer(bufferInfo, 10_000)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> handleOutputFormat(codecValue.outputFormat)
                        else -> if (outputIndex >= 0) {
                            val payload = codecValue.getOutputBuffer(outputIndex)?.extractBytes(bufferInfo) ?: ByteArray(0)
                            try {
                                handleEncodedBuffer(payload, bufferInfo)
                            } finally {
                                codecValue.releaseOutputBuffer(outputIndex, false)
                            }
                        }
                    }
                }
            }.also {
                it.name = "gomtm-screen-stream-encode"
                it.isDaemon = true
                it.start()
            }
        }

        private fun handleOutputFormat(format: MediaFormat) {
            codecConfig = buildCodecConfig(format)
            codecString = buildAvcCodecString(format)
        }

        private fun handleEncodedBuffer(payload: ByteArray, bufferInfo: MediaCodec.BufferInfo) {
            if (payload.isEmpty()) {
                return
            }
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                codecConfig = payload
                return
            }
            val isKeyframe = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
            val currentCodecConfig = codecConfig
            val accessUnit = if (isKeyframe && currentCodecConfig != null && currentCodecConfig.isNotEmpty()) {
                currentCodecConfig + payload
            } else {
                payload
            }
            writeFrame(bufferInfo.presentationTimeUs, isKeyframe, accessUnit)
        }

        private fun writeFrame(ptsUs: Long, isKeyframe: Boolean, accessUnit: ByteArray) {
            val header =
                org.json.JSONObject()
                    .put("v", 1)
                    .put("pts_us", ptsUs)
                    .put("is_keyframe", isKeyframe)
                    .toString()
                    .toByteArray(Charsets.UTF_8)
            synchronized(this) {
                val output = activeOutput ?: return
                runCatching {
                    writeLengthPrefixed(output, header)
                    writeLengthPrefixed(output, accessUnit)
                    output.flush()
                }.onFailure {
                    closeClient()
                }
            }
        }

        private fun requestSyncFrame() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                runCatching {
                    codec?.setParameters(android.os.Bundle().apply {
                        putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                    })
                }
            }
        }

        private fun closeClient() {
            runCatching { activeOutput?.close() }
            runCatching { activeClient?.close() }
            activeOutput = null
            activeClient = null
        }

        private val projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                lastErrorMessage = "screen capture permission was revoked"
                stop()
            }
        }
    }

    private data class DisplayMetricsSnapshot(
        val width: Int,
        val height: Int,
        val densityDpi: Int,
    )

    private fun currentDisplayMetrics(context: Context): DisplayMetricsSnapshot {
        val windowManager = context.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds: Rect = windowManager.currentWindowMetrics.bounds
            DisplayMetricsSnapshot(
                width = bounds.width(),
                height = bounds.height(),
                densityDpi = context.resources.displayMetrics.densityDpi,
            )
        } else {
            val metrics = context.resources.displayMetrics
            DisplayMetricsSnapshot(
                width = metrics.widthPixels,
                height = metrics.heightPixels,
                densityDpi = metrics.densityDpi,
            )
        }
    }

    private fun MediaCodec.outputBuffer(index: Int): ByteBuffer? = getOutputBuffer(index)

    private fun ByteBuffer.extractBytes(bufferInfo: MediaCodec.BufferInfo): ByteArray {
        position(bufferInfo.offset)
        limit(bufferInfo.offset + bufferInfo.size)
        return ByteArray(bufferInfo.size).also { get(it) }
    }

    private fun buildCodecConfig(format: MediaFormat): ByteArray? {
        val sps = format.getByteBuffer("csd-0")?.remainingCopy() ?: return null
        val pps = format.getByteBuffer("csd-1")?.remainingCopy() ?: return sps
        return sps + pps
    }

    private fun buildAvcCodecString(format: MediaFormat): String {
        val sps = format.getByteBuffer("csd-0")?.remainingCopy() ?: return "avc1"
        val nal = extractNalPayload(sps)
        if (nal.size < 4) {
            return "avc1"
        }
        return String.format(
            Locale.US,
            "avc1.%02x%02x%02x",
            nal[1].toInt() and 0xFF,
            nal[2].toInt() and 0xFF,
            nal[3].toInt() and 0xFF,
        )
    }

    private fun ByteBuffer.remainingCopy(): ByteArray {
        val copy = duplicate()
        val result = ByteArray(copy.remaining())
        copy.get(result)
        return result
    }

    private fun extractNalPayload(bytes: ByteArray): ByteArray {
        var index = 0
        while (index + 3 < bytes.size) {
            if (bytes[index] == 0.toByte() && bytes[index + 1] == 0.toByte()) {
                if (bytes[index + 2] == 1.toByte()) {
                    return bytes.copyOfRange(index + 3, bytes.size)
                }
                if (index + 4 < bytes.size && bytes[index + 2] == 0.toByte() && bytes[index + 3] == 1.toByte()) {
                    return bytes.copyOfRange(index + 4, bytes.size)
                }
            }
            index += 1
        }
        return bytes
    }

    private fun writeLengthPrefixed(output: OutputStream, payload: ByteArray) {
        val header = ByteBuffer.allocate(4).putInt(payload.size).array()
        output.write(header)
        output.write(payload)
    }
}
