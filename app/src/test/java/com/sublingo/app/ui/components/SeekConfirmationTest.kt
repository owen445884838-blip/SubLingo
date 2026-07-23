package com.sublingo.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekConfirmationTest {
    @Test fun pendingSeekIgnoresOldPositionUntilConfirmedOrTimedOut() {
        assertFalse(isSeekConfirmed(currentMs = 0, targetMs = 300_000, reportedPositionMs = null))
        assertTrue(isSeekConfirmed(currentMs = 299_000, targetMs = 300_000, reportedPositionMs = null))
        assertTrue(isSeekConfirmed(currentMs = 0, targetMs = 300_000, reportedPositionMs = 300_000))
        assertFalse(isSeekConfirmed(currentMs = 0, targetMs = 300_000, reportedPositionMs = 0))
        assertFalse(seekConfirmationTimedOut(nowMs = 2_500, startedAtMs = 1_000))
        assertTrue(seekConfirmationTimedOut(nowMs = 3_000, startedAtMs = 1_000))
    }
}
