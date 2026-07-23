package com.sublingo.app.ui.transcript

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptPlaybackTest {
    @Test fun sentenceOnlyPausesAfterPlaybackEnteredItsTargetRange() {
        assertEquals(false, shouldPauseSentence(positionMs = 50_000L, endMs = 10_000L, entered = false))
        assertEquals(false, shouldPauseSentence(positionMs = 9_999L, endMs = 10_000L, entered = true))
        assertEquals(true, shouldPauseSentence(positionMs = 10_000L, endMs = 10_000L, entered = true))
    }
}
