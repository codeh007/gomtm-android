package com.gomtm.swarm.platform.python

import java.io.File

object PythonRuntimeNative {
    const val LAUNCHER_LIBRARY = "embedded_python"

    @Volatile
    private var loaded = false

    fun load(nativeLibraryDir: String) {
        if (loaded) {
            return
        }
        synchronized(this) {
            if (loaded) {
                return
            }
            val libFile = File(nativeLibraryDir, "lib${LAUNCHER_LIBRARY}.so")
            if (libFile.exists()) {
                System.load(libFile.absolutePath)
            } else {
                System.loadLibrary(LAUNCHER_LIBRARY)
            }
            loaded = true
        }
    }

    external fun runMain(
        pythonHome: String,
        sourceApkPath: String,
        nativeLibraryDir: String,
        args: Array<String>,
    ): Int
}
