package com.gomtm.swarm.platform.lifecycle

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GomtmForegroundServiceContractTest {
    @Test
    fun foregroundServiceStartsP2pandroidRuntime() {
        val source = readProjectFile("src/main/java/com/gomtm/swarm/platform/lifecycle/GomtmForegroundService.kt")

        assertTrue(source.contains("P2pandroid.startNodeWithOptions"))
    }

    private fun readProjectFile(relativePath: String): String {
        val root = File(requireNotNull(System.getProperty("user.dir")))
        return File(root, relativePath).readText()
    }
}
