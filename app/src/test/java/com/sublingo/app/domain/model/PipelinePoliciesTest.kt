package com.sublingo.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PipelinePoliciesTest {
    @Test fun choosesFlashThenStandardAndRejectsOverFiveHours() {
        assertEquals("FLASH", PipelinePolicies.asrMode(30 * 60_000L))
        assertEquals("STANDARD", PipelinePolicies.asrMode(3 * 60 * 60_000L))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            PipelinePolicies.asrMode(6 * 60 * 60_000L)
        }
    }

    @Test fun successfulChunksAreNotRetried() {
        assertEquals(listOf(1, 3), PipelinePolicies.pendingChunkIndexes(listOf("SUCCEEDED", "FAILED", "SUCCEEDED", "PENDING")))
    }
}
