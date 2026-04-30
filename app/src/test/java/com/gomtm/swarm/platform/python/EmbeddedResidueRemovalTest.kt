package com.gomtm.swarm.platform.python

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class EmbeddedResidueRemovalTest {
    @Test
    fun embeddedPythonSourceFilesNoLongerExist() {
        val moduleDir = File(requireNotNull(System.getProperty("user.dir")))
        assertFalse(File(moduleDir, "src/main/java/com/gomtm/swarm/platform/python/EmbeddedPythonPaths.kt").exists())
        assertFalse(File(moduleDir, "src/main/java/com/gomtm/swarm/platform/python/EmbeddedPythonBootstrap.kt").exists())
        assertFalse(File(moduleDir, "src/main/java/com/gomtm/swarm/platform/python/EmbeddedPythonNative.kt").exists())
    }
}
