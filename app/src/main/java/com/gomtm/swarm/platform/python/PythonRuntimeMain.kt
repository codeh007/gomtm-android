package com.gomtm.swarm.platform.python

import kotlin.system.exitProcess

data class PythonRuntimeLaunchArgs(
    val pythonHome: String,
    val sourceApkPath: String,
    val nativeLibraryDir: String,
    val pythonArgs: List<String>,
)

object PythonRuntimeMain {
    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(
            runForTest(
            args = args,
            load = { path -> PythonRuntimeNative.load(path) },
            run = { parsed ->
                PythonRuntimeNative.runMain(
                    parsed.pythonHome,
                    parsed.sourceApkPath,
                    parsed.nativeLibraryDir,
                    parsed.pythonArgs.toTypedArray(),
                )
            },
        ),
        )
    }

    fun invokeNative(parsed: PythonRuntimeLaunchArgs, runner: (PythonRuntimeLaunchArgs) -> Int): Int {
        return runner(parsed)
    }

    fun runForTest(
        args: Array<String>,
        load: (String) -> Unit,
        run: (PythonRuntimeLaunchArgs) -> Int,
    ): Int {
        val parsed = parseArgs(args)
        load(parsed.nativeLibraryDir)
        return invokeNative(parsed, run)
    }

    fun parseArgs(args: Array<String>): PythonRuntimeLaunchArgs {
        var pythonHome = ""
        var sourceApkPath = ""
        var nativeLibraryDir = ""
        val pythonArgs = mutableListOf<String>()

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--python-home" -> {
                    pythonHome = args.getOrElse(i + 1) { "" }
                    i += 2
                }

                "--source-apk" -> {
                    sourceApkPath = args.getOrElse(i + 1) { "" }
                    i += 2
                }

                "--native-lib-dir" -> {
                    nativeLibraryDir = args.getOrElse(i + 1) { "" }
                    i += 2
                }

                "--" -> {
                    pythonArgs += args.drop(i + 1)
                    break
                }

                else -> {
                    pythonArgs += args.drop(i)
                    break
                }
            }
        }

        return PythonRuntimeLaunchArgs(
            pythonHome = pythonHome,
            sourceApkPath = sourceApkPath,
            nativeLibraryDir = nativeLibraryDir,
            pythonArgs = pythonArgs,
        )
    }
}
