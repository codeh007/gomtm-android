package com.gomtm.swarm.platform.python

import org.junit.Assert.assertTrue
import org.junit.Test

class PythonRuntimeNativeBridgeTest {
    @Test
    fun launcherEntrypointNameStaysStable() {
        assertTrue(PythonRuntimeNative.LAUNCHER_LIBRARY == "embedded_python")
    }
}
