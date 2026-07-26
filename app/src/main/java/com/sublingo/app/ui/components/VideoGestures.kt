package com.sublingo.app.ui.components

enum class VideoDoubleTapAction { REWIND, TOGGLE_PLAYBACK, FORWARD }

data class PlaybackPositionUpdate(
    val videoId: String,
    val positionMs: Long,
)

object PlaybackPositionHandoff {
    private val pending = androidx.compose.runtime.mutableStateOf<PlaybackPositionUpdate?>(null)

    @Synchronized
    fun publish(videoId: String, positionMs: Long) {
        pending.value = PlaybackPositionUpdate(videoId, positionMs.coerceAtLeast(0L))
    }

    @Synchronized
    fun peek(videoId: String): PlaybackPositionUpdate? = pending.value?.takeIf { it.videoId == videoId }

    @Synchronized
    fun consume(update: PlaybackPositionUpdate) {
        if (pending.value === update) pending.value = null
    }
}

fun videoDoubleTapAction(x: Float, width: Float): VideoDoubleTapAction {
    if (width <= 0f) return VideoDoubleTapAction.TOGGLE_PLAYBACK
    return when {
        x < width / 3f -> VideoDoubleTapAction.REWIND
        x > width * 2f / 3f -> VideoDoubleTapAction.FORWARD
        else -> VideoDoubleTapAction.TOGGLE_PLAYBACK
    }
}
