package com.sublingo.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoScrubberTest {
    @Test fun horizontalTouchMapsToBoundedVideoPosition() {
        assertEquals(0L, scrubPosition(-20, 10_000L))
        assertEquals(5_000L, scrubPosition(5_000, 10_000L))
        assertEquals(10_000L, scrubPosition(15_000, 10_000L))
        assertEquals(0L, scrubPosition(5_000, 0L))
    }
}
