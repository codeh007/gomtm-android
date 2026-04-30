package com.gomtm.swarm.platform.python

import android.content.Context
import java.io.File

object PythonRuntimeCommand {
    const val PROBE_VERSION = "version"
    const val PROBE_STDLIB_SMOKE = "stdlib-smoke"
    const val PROBE_BASE_PACKAGE_SMOKE = "base-package-smoke"
    const val PROBE_WORKER_SMOKE = "worker-smoke"

    fun runProbe(context: Context, probe: String): PythonRuntimeProbeResult {
        val installer = PythonRuntimeInstaller(context)
        val status = installer.inspect()
        if (!status.installed) {
            return PythonRuntimeProbeResult(
                probe = probe,
                success = false,
                exitCode = -1,
                stderr = status.message,
            )
        }

        val pythonPath = status.pythonPath ?: return PythonRuntimeProbeResult(
            probe = probe,
            success = false,
            exitCode = -1,
            stderr = "python path missing",
        )

        val currentRoot = File(pythonPath).parentFile?.parentFile
            ?: return PythonRuntimeProbeResult(
                probe = probe,
                success = false,
                exitCode = -1,
                stderr = "python runtime root missing",
            )
        val pythonHome = File(currentRoot, "prefix")

        val command = when (probe) {
            PROBE_VERSION -> listOf(pythonPath, "-V")
            PROBE_STDLIB_SMOKE -> listOf(pythonPath, "-c", "import os,sys,tempfile,json; print(f'PYTHONHOME={os.environ.get(\"PYTHONHOME\", \"\")}'); print(f'TMPDIR={os.environ.get(\"TMPDIR\", \"\")}')")
            PROBE_BASE_PACKAGE_SMOKE -> listOf(pythonPath, "-c", "import gomtm_python_base as base; print(base.package_name)")
            PROBE_WORKER_SMOKE -> listOf(pythonPath, "-c", PythonRuntimeWorkerProbe.SCRIPT)
            else -> return PythonRuntimeProbeResult(
                probe = probe,
                success = false,
                exitCode = -1,
                stderr = "unsupported probe",
            )
        }

        val process = ProcessBuilder(command)
            .directory(currentRoot)
            .apply {
                environment()["PYTHONHOME"] = pythonHome.absolutePath
                environment()["PYTHONPATH"] = File(currentRoot, "site-packages-base").absolutePath
                environment()["TMPDIR"] = context.cacheDir.absolutePath
                environment()["HOME"] = currentRoot.absolutePath
            }
            .start()

        val stdout = process.inputStream.bufferedReader().readText().trim()
        val stderr = process.errorStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        return PythonRuntimeProbeResult(
            probe = probe,
            success = exitCode == 0,
            exitCode = exitCode,
            stdout = stdout,
            stderr = stderr,
        )
    }
}
