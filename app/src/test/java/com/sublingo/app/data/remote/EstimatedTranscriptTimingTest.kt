package com.sublingo.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimatedTranscriptTimingTest {
    @Test fun splitsSentencesAndCoversWholeChunkWithoutGaps() {
        val result = EstimatedTranscriptTiming.utterances("Hello there. This is a longer sentence!", 9_000)
        assertEquals(2, result.size)
        assertEquals(0, result.first().startMs)
        assertEquals(result.first().endMs, result.last().startMs)
        assertEquals(9_000, result.last().endMs)
        assertTrue(result.last().endMs > result.first().endMs)
    }
}
