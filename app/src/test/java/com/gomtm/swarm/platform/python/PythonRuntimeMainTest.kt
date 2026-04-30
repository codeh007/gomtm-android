package com.gomtm.swarm.platform.python

import org.junit.Assert.assertEquals
import org.junit.Test

class PythonRuntimeMainTest {
    @Test
    fun parseArgsExtractsLauncherAndPythonArguments() {
        val parsed = PythonRuntimeMain.parseArgs(
            arrayOf(
                "--python-home", "/data/user/0/com.gomtm.swarm/files/runtime/python/current/prefix",
                "--source-apk", "/data/app/com.gomtm.swarm/base.apk",
                "--native-lib-dir", "/data/app/com.gomtm.swarm/lib/arm64",
                "--",
                "-V",
            ),
        )

        assertEquals("/data/user/0/com.gomtm.swarm/files/runtime/python/current/prefix", parsed.pythonHome)
        assertEquals("/data/app/com.gomtm.swarm/base.apk", parsed.sourceApkPath)
        assertEquals("/data/app/com.gomtm.swarm/lib/arm64", parsed.nativeLibraryDir)
        assertEquals(listOf("-V"), parsed.pythonArgs)
    }

    @Test
    fun invokeNativeUsesParsedArguments() {
        val parsed = PythonRuntimeLaunchArgs(
            pythonHome = "/tmp/python-home",
            sourceApkPath = "/tmp/base.apk",
            nativeLibraryDir = "/tmp/lib",
            pythonArgs = listOf("-V"),
        )

        var seen: PythonRuntimeLaunchArgs? = null
        val exit = PythonRuntimeMain.invokeNative(parsed) { args ->
            seen = args
            0
        }

        assertEquals(0, exit)
        assertEquals(parsed, seen)
    }

    @Test
    fun mainLoadsNativeLibraryBeforeRunning() {
        var loadedPath: String? = null
        var seenArgs: PythonRuntimeLaunchArgs? = null

        PythonRuntimeMain.runForTest(
            args = arrayOf(
                "--python-home", "/tmp/python-home",
                "--source-apk", "/tmp/base.apk",
                "--native-lib-dir", "/tmp/lib",
                "--",
                "-V",
            ),
            load = { path -> loadedPath = path },
            run = { parsed ->
                seenArgs = parsed
                0
            },
        )

        assertEquals("/tmp/lib", loadedPath)
        assertEquals(listOf("-V"), seenArgs?.pythonArgs)
    }
}
