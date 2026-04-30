package com.gomtm.swarm.platform.python

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PythonRuntimeCommandTest {
    @Test
    fun probeNamesStayHardCodedAndDoNotExposeEmbeddedTerminology() {
        assertTrue(PythonRuntimeCommand.PROBE_VERSION == "version")
        assertTrue(PythonRuntimeCommand.PROBE_STDLIB_SMOKE == "stdlib-smoke")
        assertTrue(PythonRuntimeCommand.PROBE_BASE_PACKAGE_SMOKE == "base-package-smoke")
        assertTrue(PythonRuntimeCommand.PROBE_WORKER_SMOKE == "worker-smoke")
    }

    @Test
    fun versionProbeReturnsStructuredFailureWhenRuntimeMissing() {
        val context = FakeContext()

        val result = PythonRuntimeCommand.runProbe(context, PythonRuntimeCommand.PROBE_VERSION)

        assertFalse(result.success)
        assertEquals(-1, result.exitCode)
        assertTrue(result.stderr.contains("not installed"))
    }

    @Test
    fun versionProbeUsesCurrentPythonWrapperWhenInstalled() {
        val context = FakeContext()
        val binDir = File(context.filesDir, "runtime/python/current/bin")
        binDir.mkdirs()
        val python = File(binDir, "python")
        python.writeText(
            """
            #!/bin/sh
            if [ "$1" = "-V" ]; then
              echo "Python 3.14.4"
              exit 0
            fi
            echo "unexpected" >&2
            exit 1
            """.trimIndent() + "\n",
        )
        python.setExecutable(true)

        val result = PythonRuntimeCommand.runProbe(context, PythonRuntimeCommand.PROBE_VERSION)

        assertTrue(result.success)
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("Python 3.14.4"))
    }

    @Test
    fun stdlibSmokeProbeUsesPythonCModeAndInjectsPythonHome() {
        val context = FakeContext()
        val currentRoot = File(context.filesDir, "runtime/python/current")
        val binDir = File(currentRoot, "bin")
        binDir.mkdirs()
        val python = File(binDir, "python")
        python.writeText(
            """
            #!/bin/sh
            if [ "$1" = "-c" ]; then
              echo "PYTHONHOME=${'$'}PYTHONHOME"
              echo "TMPDIR=${'$'}TMPDIR"
              exit 0
            fi
            exit 1
            """.trimIndent() + "\n",
        )
        python.setExecutable(true)

        val result = PythonRuntimeCommand.runProbe(context, PythonRuntimeCommand.PROBE_STDLIB_SMOKE)

        assertTrue(result.success)
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("PYTHONHOME=${currentRoot.absolutePath}/prefix"))
        assertTrue(result.stdout.contains("TMPDIR="))
    }

    private class FakeContext : ContextWrapper(null) {
        private val filesRoot = createTempDir(prefix = "gomtm-python-command-")

        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = filesRoot

        override fun getCacheDir(): File = File(filesRoot, "cache").apply { mkdirs() }
    }
}
