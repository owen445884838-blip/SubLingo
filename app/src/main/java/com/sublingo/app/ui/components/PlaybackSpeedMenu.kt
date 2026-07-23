package com.sublingo.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset

val PlaybackSpeedOptions = listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f)

fun playbackSpeedLabel(speed: Float): String = when (speed) {
    .5f -> "0.5x"
    .75f -> "0.75x"
    1f -> "1.0x"
    1.25f -> "1.25x"
    1.5f -> "1.5x"
    2f -> "2.0x"
    else -> "${speed}x"
}

@Composable
fun PlaybackSpeedMenu(
    speed: Float,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    buttonColor: Color,
    selectedColor: Color = Color(0xFFFDCF44),
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Surface(
            onClick = { onExpandedChange(true) },
            color = Color.Transparent,
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(
                playbackSpeedLabel(speed),
                Modifier.padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 7.dp else 8.dp),
                color = buttonColor,
                fontWeight = FontWeight.Bold,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            offset = DpOffset(x = (-28).dp, y = 0.dp),
            shape = RoundedCornerShape(18.dp),
            containerColor = Color.White,
            shadowElevation = 0.dp,
        ) {
            PlaybackSpeedOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            playbackSpeedLabel(option),
                            color = Color(0xFF2D2D44),
                            fontWeight = if (option == speed) FontWeight.Black else FontWeight.Medium,
                        )
                    },
                    trailingIcon = {
                        if (option == speed) Text("✓", color = selectedColor, fontWeight = FontWeight.Black)
                    },
                    onClick = {
                        onSpeedSelected(option)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}
