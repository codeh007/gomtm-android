package com.gomtm.swarm.platform.python

data class PythonRuntimeStatus(
    val installed: Boolean,
    val activeRelease: String? = null,
    val pythonPath: String? = null,
    val message: String = "",
)

data class PythonRuntimeProbeResult(
    val probe: String,
    val success: Boolean,
    val exitCode: Int,
    val stdout: String = "",
    val stderr: String = "",
)
