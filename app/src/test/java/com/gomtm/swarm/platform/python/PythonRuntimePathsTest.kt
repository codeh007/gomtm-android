package com.gomtm.swarm.platform.python

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonRuntimePathsTest {
    @Test
    fun pythonRuntimePathsStayInsideAppPrivateFilesDir() {
        val context = FakeContext()
        val paths = PythonRuntimePaths.from(context)

        val root = context.filesDir.absolutePath
        assertTrue(paths.runtimeRoot.startsWith(root))
        assertTrue(paths.releasesRoot.startsWith(root))
        assertTrue(paths.currentLink.startsWith(root))
        assertTrue(paths.overlaysRoot.startsWith(root))
        assertTrue(paths.logsRoot.startsWith(root))
        assertTrue(paths.runRoot.startsWith(root))
    }

    private class FakeContext : ContextWrapper(null) {
        private val filesRoot = createTempDir(prefix = "gomtm-python-runtime-")

        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = filesRoot
    }
}
