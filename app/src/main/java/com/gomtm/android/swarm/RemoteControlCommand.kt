package com.gomtm.android.swarm

import org.json.JSONObject

data class RemoteControlCommandRequest(
    val requestId: String,
    val command: String,
    val params: Map<String, Any?> = emptyMap(),
)

data class RemoteControlCommandResponse(
    val requestId: String,
    val ok: Boolean,
    val payloadJson: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val retryable: Boolean = false,
)

data class RemoteControlScreenshotPayload(
    val mimeType: String,
    val imageBase64: String,
    val width: Int,
    val height: Int,
    val capturedAt: String,
)

data class RemoteControlActionPayload(
    val status: String = "ok",
    val message: String? = null,
)

data class RemoteControlCapabilityState(
    val state: String,
    val reason: String? = null,
)

data class RemoteControlStreamResolvedTarget(
    val kind: String,
    val host: String,
    val port: Int,
    val protocolHint: String? = null,
    val serviceHint: String? = null,
)

data class RemoteControlStreamChannelPayload(
    val kind: String,
    val framing: String,
    val codec: String,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val keyframeRequiredOnStart: Boolean,
)

data class RemoteControlScreenStreamPayload(
    val status: String,
    val resolved: RemoteControlStreamResolvedTarget,
    val channel: RemoteControlStreamChannelPayload,
    val lastError: String? = null,
)

data class RemoteControlPermissionState(
    val accessibility: String,
    val screenCapture: String,
)

sealed interface RemoteControlCommandResult<out T> {
    data class Success<T>(val payload: T) : RemoteControlCommandResult<T>

    data class Error(
        val code: String,
        val message: String,
        val retryable: Boolean,
    ) : RemoteControlCommandResult<Nothing>
}

interface RemoteControlOps {
    fun screenSnapshot(format: String): RemoteControlCommandResult<RemoteControlScreenshotPayload>

    fun screenStreamEnsure(): RemoteControlCommandResult<RemoteControlScreenStreamPayload>

    fun screenStreamStop(): RemoteControlCommandResult<RemoteControlActionPayload>

    fun inputTap(x: Int, y: Int): RemoteControlCommandResult<RemoteControlActionPayload>

    fun inputSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int): RemoteControlCommandResult<RemoteControlActionPayload>

    fun inputText(text: String): RemoteControlCommandResult<RemoteControlActionPayload>

    fun inputKey(key: String): RemoteControlCommandResult<RemoteControlActionPayload>
}

fun parseRemoteControlRequest(raw: String): RemoteControlCommandRequest? {
    if (raw.isBlank()) {
        return null
    }
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val requestId = json.optString("request_id").trim()
    val command = json.optString("command").trim()
    if (requestId.isEmpty() || command.isEmpty()) {
        return null
    }
    val paramsObject = json.optJSONObject("params")
    return RemoteControlCommandRequest(
        requestId = requestId,
        command = command,
        params = paramsObject?.toMap().orEmpty(),
    )
}

fun encodeRemoteControlResponse(response: RemoteControlCommandResponse): String {
    return JSONObject()
        .put("request_id", response.requestId)
        .put("ok", response.ok)
        .apply {
            if (!response.payloadJson.isNullOrBlank()) {
                put("payload_json", response.payloadJson)
            }
            if (!response.errorCode.isNullOrBlank()) {
                put("error_code", response.errorCode)
            }
            if (!response.errorMessage.isNullOrBlank()) {
                put("error_message", response.errorMessage)
            }
            if (response.retryable) {
                put("retryable", true)
            }
        }
        .toString()
}

fun deriveRemoteControlPermissionState(
    accessibilityEnabled: Boolean,
    screenshotSupported: Boolean,
): RemoteControlPermissionState {
    val accessibility = if (accessibilityEnabled) "granted" else "not_granted"
    val screenCapture = when {
        !screenshotSupported -> "unsupported"
        accessibilityEnabled -> "granted"
        else -> "not_granted"
    }
    return RemoteControlPermissionState(accessibility = accessibility, screenCapture = screenCapture)
}

fun handleRemoteControlRequest(
    request: RemoteControlCommandRequest,
    ops: RemoteControlOps,
): RemoteControlCommandResponse {
    return when (request.command) {
        "screen.snapshot" -> when (val result = ops.screenSnapshot(request.params["format"]?.toString().orEmpty().ifBlank { "png" })) {
            is RemoteControlCommandResult.Success -> successResponse(request.requestId, result.payload.toJson())
            is RemoteControlCommandResult.Error -> errorResponse(request.requestId, result)
        }

        "screen.stream.ensure" -> when (val result = ops.screenStreamEnsure()) {
            is RemoteControlCommandResult.Success -> successResponse(request.requestId, result.payload.toJson())
            is RemoteControlCommandResult.Error -> errorResponse(request.requestId, result)
        }

        "screen.stream.stop" -> when (val result = ops.screenStreamStop()) {
            is RemoteControlCommandResult.Success -> successResponse(request.requestId, result.payload.toJson())
            is RemoteControlCommandResult.Error -> errorResponse(request.requestId, result)
        }

        "input.tap" -> when (val result = ops.inputTap(request.requireInt("x"), request.requireInt("y"))) {
            is RemoteControlCommandResult.Success -> successResponse(request.requestId, result.payload.toJson())
            is RemoteControlCommandResult.Error -> errorResponse(request.requestId, result)
        }

        "input.swipe" -> when (
            val result = ops.inputSwipe(
                request.requireInt("start_x"),
                request.requireInt("start_y"),
                request.requireInt("end_x"),
                request.requireInt("end_y"),
                request.optionalInt("duration_ms"),
            )
        ) {
            is RemoteControlCommandResult.Success -> successResponse(request.requestId, result.payload.toJson())
            is RemoteControlCommandResult.Error -> errorResponse(request.requestId, result)
        }

        "input.text" -> when (val result = ops.inputText(request.params["text"]?.toString().orEmpty())) {
            is RemoteControlCommandResult.Success -> successResponse(request.requestId, result.payload.toJson())
            is RemoteControlCommandResult.Error -> errorResponse(request.requestId, result)
        }

        "input.key" -> when (val result = ops.inputKey(request.params["key"]?.toString().orEmpty())) {
            is RemoteControlCommandResult.Success -> successResponse(request.requestId, result.payload.toJson())
            is RemoteControlCommandResult.Error -> errorResponse(request.requestId, result)
        }

        else -> RemoteControlCommandResponse(
            requestId = request.requestId,
            ok = false,
            errorCode = "SB_CAPABILITY_UNAVAILABLE",
            errorMessage = "unsupported remote control command: ${request.command}",
            retryable = false,
        )
    }
}

private fun successResponse(requestId: String, payloadJson: String): RemoteControlCommandResponse {
    return RemoteControlCommandResponse(requestId = requestId, ok = true, payloadJson = payloadJson)
}

private fun errorResponse(requestId: String, error: RemoteControlCommandResult.Error): RemoteControlCommandResponse {
    return RemoteControlCommandResponse(
        requestId = requestId,
        ok = false,
        errorCode = error.code,
        errorMessage = error.message,
        retryable = error.retryable,
    )
}

private fun RemoteControlCommandRequest.requireInt(key: String): Int {
    return optionalInt(key)
}

private fun RemoteControlCommandRequest.optionalInt(key: String): Int {
    return when (val value = params[key]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: 0
        else -> 0
    }
}

private fun JSONObject.toMap(): Map<String, Any?> {
    val result = linkedMapOf<String, Any?>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        result[key] = normalizeJsonValue(opt(key))
    }
    return result
}

private fun normalizeJsonValue(value: Any?): Any? {
    return when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.toMap()
        else -> value
    }
}

private fun RemoteControlScreenshotPayload.toJson(): String {
    return JSONObject()
        .put("mime_type", mimeType)
        .put("image_base64", imageBase64)
        .put("width", width)
        .put("height", height)
        .put("captured_at", capturedAt)
        .toString()
}

private fun RemoteControlActionPayload.toJson(): String {
    return JSONObject()
        .put("status", status)
        .apply {
            if (!message.isNullOrBlank()) {
                put("message", message)
            }
        }
        .toString()
}

private fun RemoteControlScreenStreamPayload.toJson(): String {
    return JSONObject()
        .put("status", status)
        .put("resolved", resolved.toJson())
        .put("channel", channel.toJson())
        .apply {
            if (!lastError.isNullOrBlank()) {
                put("last_error", lastError)
            }
        }
        .toString()
}

private fun RemoteControlStreamResolvedTarget.toJson(): JSONObject {
    return JSONObject()
        .put("kind", kind)
        .put("host", host)
        .put("port", port)
        .apply {
            if (!protocolHint.isNullOrBlank()) {
                put("protocol_hint", protocolHint)
            }
            if (!serviceHint.isNullOrBlank()) {
                put("service_hint", serviceHint)
            }
        }
}

private fun RemoteControlStreamChannelPayload.toJson(): JSONObject {
    return JSONObject()
        .put("kind", kind)
        .put("framing", framing)
        .put("codec", codec)
        .put("width", width)
        .put("height", height)
        .put("rotation", rotation)
        .put("keyframe_required_on_start", keyframeRequiredOnStart)
}
