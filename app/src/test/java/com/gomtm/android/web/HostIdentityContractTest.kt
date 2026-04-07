package com.gomtm.swarm.web

import com.gomtm.swarm.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class HostIdentityContractTest {
    @Test
    fun usesSwarmApplicationId() {
        assertEquals("com.gomtm.swarm", BuildConfig.APPLICATION_ID)
    }
}
