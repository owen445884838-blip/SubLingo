package com.sublingo.app.data.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageCleanupPolicyTest {
    @Test
    fun onlyOldPartFilesAreEligibleForStartupCleanup() {
        val now = 2 * StorageCleanupPolicy.PART_FILE_RETENTION_MS
        assertTrue(
            StorageCleanupPolicy.shouldDeletePartFile(
                "media.mp4.part",
                now - StorageCleanupPolicy.PART_FILE_RETENTION_MS,
                now,
            ),
        )
        assertFalse(StorageCleanupPolicy.shouldDeletePartFile("media.mp4", 1L, now))
        assertFalse(StorageCleanupPolicy.shouldDeletePartFile("media.mp4.part", now - 1_000L, now))
        assertFalse(StorageCleanupPolicy.shouldDeletePartFile("media.mp4.part", 0L, now))
    }
}
