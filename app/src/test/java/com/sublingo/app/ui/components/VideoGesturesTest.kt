package com.sublingo.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoGesturesTest {
    @Test fun doubleTapUsesLeftCenterAndRightThirds() {
        assertEquals(VideoDoubleTapAction.REWIND, videoDoubleTapAction(10f, 300f))
        assertEquals(VideoDoubleTapAction.TOGGLE_PLAYBACK, videoDoubleTapAction(150f, 300f))
        assertEquals(VideoDoubleTapAction.FORWARD, videoDoubleTapAction(290f, 300f))
    }

    @Test fun invalidWidthFallsBackToPlaybackToggle() {
        assertEquals(VideoDoubleTapAction.TOGGLE_PLAYBACK, videoDoubleTapAction(10f, 0f))
    }
}
