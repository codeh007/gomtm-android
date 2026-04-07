package com.gomtm.swarm.web

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseWorkflowNamingTest {
    @Test
    fun releaseWorkflowUsesGomtmSwarmPublicNaming() {
        val workflow = String(Files.readAllBytes(resolveReleaseWorkflowPath()))

        assertTrue(
            "release workflow should publish gomtm-swarm named release",
            workflow.contains("name: gomtm-swarm ${'$'}{{ env.RELEASE_TAG }}"),
        )
        assertTrue("release workflow should only publish versioned tags", workflow.contains("tags:"))
        assertTrue("release workflow should publish renamed apk asset", workflow.contains("gomtm-swarm-arm64-v8a-debug.apk"))
        assertTrue("release workflow should publish renamed provenance asset", workflow.contains("gomtm-swarm-provenance.json"))
        assertFalse("release workflow should not keep gomtm-android public release copy", workflow.contains("gomtm-android installable bootstrap APK"))
        assertFalse("release workflow should not auto-release on main branch pin updates", workflow.contains("branches:"))
    }

    private fun resolveReleaseWorkflowPath(): Path {
        val candidates = listOf(
            Paths.get(".github/workflows/release.yml"),
            Paths.get("../.github/workflows/release.yml"),
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("release workflow not found")
    }
}
