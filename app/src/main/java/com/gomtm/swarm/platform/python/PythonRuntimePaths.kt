package com.gomtm.swarm.platform.python

import android.content.Context
import java.io.File

data class PythonRuntimePaths(
    val runtimeRoot: String,
    val releasesRoot: String,
    val currentLink: String,
    val overlaysRoot: String,
    val wheelhouseRoot: String,
    val logsRoot: String,
    val runRoot: String,
) {
    companion object {
        fun from(context: Context): PythonRuntimePaths {
            val root = File(context.filesDir, "runtime/python")
            return PythonRuntimePaths(
                runtimeRoot = root.absolutePath,
                releasesRoot = File(root, "releases").absolutePath,
                currentLink = File(root, "current").absolutePath,
                overlaysRoot = File(root, "overlays").absolutePath,
                wheelhouseRoot = File(root, "wheelhouse").absolutePath,
                logsRoot = File(root, "logs").absolutePath,
                runRoot = File(root, "run").absolutePath,
            )
        }
    }
}
