package com.sublingo.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoGesturesTest {
    @Test fun playbackPositionHandoffIsOneShotAndVideoScoped() {
        PlaybackPositionHandoff.publish("video-a", 12_345L)

        assertEquals(null, PlaybackPositionHandoff.peek("video-b"))
        val update = PlaybackPositionHandoff.peek("video-a")!!
        assertEquals(12_345L, update.positionMs)
        PlaybackPositionHandoff.consume(update)
        assertEquals(null, PlaybackPositionHandoff.peek("video-a"))
    }

    @Test fun doubleTapUsesLeftCenterAndRightThirds() {
        assertEquals(VideoDoubleTapAction.REWIND, videoDoubleTapAction(10f, 300f))
        assertEquals(VideoDoubleTapAction.TOGGLE_PLAYBACK, videoDoubleTapAction(150f, 300f))
        assertEquals(VideoDoubleTapAction.FORWARD, videoDoubleTapAction(290f, 300f))
    }

    @Test fun invalidWidthFallsBackToPlaybackToggle() {
        assertEquals(VideoDoubleTapAction.TOGGLE_PLAYBACK, videoDoubleTapAction(10f, 0f))
    }
}
