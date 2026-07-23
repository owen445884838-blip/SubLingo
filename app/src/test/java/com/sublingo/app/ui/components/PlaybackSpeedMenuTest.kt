package com.sublingo.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedMenuTest {
    @Test fun sharedMenuContainsCompleteLearningSpeedRange() {
        assertEquals(listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f), PlaybackSpeedOptions)
        assertEquals("0.5x", playbackSpeedLabel(.5f))
        assertEquals("1.0x", playbackSpeedLabel(1f))
        assertEquals("2.0x", playbackSpeedLabel(2f))
    }
}
