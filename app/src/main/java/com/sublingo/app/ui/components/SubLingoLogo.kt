package com.sublingo.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sublingo.app.R

@Composable
fun SubLingoLogo(
    modifier: Modifier = Modifier,
    width: Dp = 160.dp,
    height: Dp = 56.dp,
    fontSize: TextUnit = 32.sp,
    textColor: Color = Color(0xFF2E303A),
) {
    Box(
        modifier = modifier.width(width).height(height),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.sublingo_logo_background),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = 1.1f, scaleY = 1.1f),
            contentScale = ContentScale.Fit,
        )
        Text(
            text = "SubLingo",
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
