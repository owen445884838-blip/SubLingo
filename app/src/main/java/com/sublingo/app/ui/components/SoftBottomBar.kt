package com.sublingo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sublingo.app.R

enum class BottomDestination { Videos, Review, Settings }

@Composable
fun SoftBottomBar(selected: BottomDestination, onVideosClick: () -> Unit, onReviewClick: () -> Unit, onSettingsClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = .96f),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(7.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            NavItem(
                icon = painterResource(if (selected == BottomDestination.Videos) R.drawable.ic_nav_video_filled else R.drawable.ic_nav_video_outlined),
                label = "视频",
                selected = selected == BottomDestination.Videos,
                onClick = onVideosClick,
            )
            NavItem(
                icon = painterResource(if (selected == BottomDestination.Review) R.drawable.ic_nav_review_filled else R.drawable.ic_nav_review_outlined),
                label = "复习",
                selected = selected == BottomDestination.Review,
                onClick = onReviewClick,
            )
            NavItem(
                icon = painterResource(if (selected == BottomDestination.Settings) R.drawable.ic_nav_settings_filled else R.drawable.ic_nav_settings_outlined),
                label = "设置",
                selected = selected == BottomDestination.Settings,
                onClick = onSettingsClick,
            )
        }
    }
}

@Composable
private fun NavItem(icon: Painter, label: String, selected: Boolean, onClick: () -> Unit) {
    val contentColor = if (selected) Color(0xFF2E303A) else Color(0xFF747688)
    Surface(onClick = onClick, shape = RoundedCornerShape(999.dp), color = if (selected) Color(0xFFFDCF44) else Color.Transparent) {
        Column(
            Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(if (selected) 28.dp else 22.dp),
                )
            }
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
