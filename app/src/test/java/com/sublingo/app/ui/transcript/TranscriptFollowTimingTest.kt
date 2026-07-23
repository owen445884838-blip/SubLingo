package com.sublingo.app.ui.transcript

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptFollowTimingTest {
    private val rows = listOf(
        TranscriptRow(0, 0, 1_000, "First", "第一句"),
        TranscriptRow(1, 1_300, 2_000, "Second", "第二句"),
    )

    @Test fun nextSentenceIsPositionedImmediatelyWhenPreviousCueEnds() {
        assertEquals(0, nextTranscriptFollowTarget(rows, 999, 0)?.sequence)
        assertEquals(1, nextTranscriptFollowTarget(rows, 1_000, 0)?.sequence)
    }
}
