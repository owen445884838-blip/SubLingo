package com.sublingo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VideoSeekFeedback(
    action: VideoDoubleTapAction?,
    direction: VideoDoubleTapAction? = null,
    modifier: Modifier = Modifier,
) {
    val displayedAction = direction ?: action
    val visible = action != null && action == displayedAction &&
        action in setOf(VideoDoubleTapAction.REWIND, VideoDoubleTapAction.FORWARD)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(120)) + scaleIn(initialScale = .82f, animationSpec = tween(180, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(160)) + scaleOut(targetScale = .94f, animationSpec = tween(160)),
    ) {
        Row(
            modifier = Modifier
                .background(Color.Black.copy(alpha = .56f), RoundedCornerShape(999.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (displayedAction == VideoDoubleTapAction.REWIND) Text("↶", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("10", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
            if (displayedAction == VideoDoubleTapAction.FORWARD) Text("↷", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        }
    }
}
