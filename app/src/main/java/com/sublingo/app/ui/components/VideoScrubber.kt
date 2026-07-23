package com.sublingo.app.ui.components

import android.content.res.ColorStateList
import android.widget.SeekBar
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val SCRUBBER_STEPS = 10_000

@Composable
fun VideoScrubber(
    positionMs: Long,
    durationMs: Long,
    onSeekingChange: (Boolean) -> Unit,
    onPreview: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    val latestDuration = rememberUpdatedState(durationMs)
    val latestSeekingChange = rememberUpdatedState(onSeekingChange)
    val latestPreview = rememberUpdatedState(onPreview)
    val latestSeek = rememberUpdatedState(onSeek)
    AndroidView(
        factory = { context ->
            SeekBar(context).apply {
                max = SCRUBBER_STEPS
                splitTrack = false
                progressTintList = ColorStateList.valueOf(activeColor.toArgb())
                progressBackgroundTintList = ColorStateList.valueOf(inactiveColor.toArgb())
                thumbTintList = ColorStateList.valueOf(activeColor.toArgb())
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(seekBar: SeekBar) = latestSeekingChange.value(true)

                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (fromUser) latestPreview.value(scrubPosition(progress, latestDuration.value))
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        val target = scrubPosition(seekBar.progress, latestDuration.value)
                        latestPreview.value(target)
                        latestSeek.value(target)
                        latestSeekingChange.value(false)
                    }
                })
            }
        },
        update = { seekBar ->
            val targetProgress = if (durationMs > 0L) {
                (positionMs.toDouble() / durationMs * SCRUBBER_STEPS).toInt().coerceIn(0, SCRUBBER_STEPS)
            } else 0
            if (!seekBar.isPressed && seekBar.progress != targetProgress) seekBar.progress = targetProgress
            seekBar.isEnabled = durationMs > 0L
            seekBar.progressTintList = ColorStateList.valueOf(activeColor.toArgb())
            seekBar.progressBackgroundTintList = ColorStateList.valueOf(inactiveColor.toArgb())
            seekBar.thumbTintList = ColorStateList.valueOf(activeColor.toArgb())
        },
        modifier = modifier.fillMaxWidth().height(32.dp),
    )
}

internal fun scrubPosition(progress: Int, durationMs: Long): Long =
    if (durationMs <= 0L) 0L
    else (durationMs * progress.coerceIn(0, SCRUBBER_STEPS) / SCRUBBER_STEPS)
