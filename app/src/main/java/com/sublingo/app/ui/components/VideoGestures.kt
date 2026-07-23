package com.sublingo.app.ui.components

enum class VideoDoubleTapAction { REWIND, TOGGLE_PLAYBACK, FORWARD }

fun videoDoubleTapAction(x: Float, width: Float): VideoDoubleTapAction {
    if (width <= 0f) return VideoDoubleTapAction.TOGGLE_PLAYBACK
    return when {
        x < width / 3f -> VideoDoubleTapAction.REWIND
        x > width * 2f / 3f -> VideoDoubleTapAction.FORWARD
        else -> VideoDoubleTapAction.TOGGLE_PLAYBACK
    }
}
