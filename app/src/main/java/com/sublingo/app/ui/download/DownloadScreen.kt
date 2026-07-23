package com.sublingo.app.ui.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sublingo.app.ui.components.PillInput
import com.sublingo.app.ui.components.SubLingoLogo

private val Cream = Color(0xFFFDFAF0)
private val Ink = Color(0xFF2E303A)
private val Muted = Color(0xFF747688)
private val Purple = Color(0xFF8B6DF1)
private val Gold = Color(0xFFF8C62A)

@Composable
fun DownloadScreen(viewModel: DownloadViewModel) {
    val state by viewModel.uiState.collectAsState()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importLocal)
    }

    if (state.youtubeLoginRequired) {
        YoutubeLoginDialog(
            targetVideoUrl = state.youtubeLoginUrl.ifBlank { state.url },
            onAuthenticated = viewModel::completeYoutubeLogin,
            onDismiss = viewModel::dismissYoutubeLogin,
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Cream).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        SubLingoLogo(modifier = Modifier.align(Alignment.CenterHorizontally))
        Column {
            Text("新学习任务", color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(Purple).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(999.dp)).background(Color(0xFFF0EAFF)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    PillInput(value = state.url, onValueChange = viewModel::updateUrl, placeholder = "在此粘贴视频链接...")
                }
                Spacer(Modifier.width(8.dp))
                Surface(onClick = viewModel::createOrResume, color = Gold, shape = RoundedCornerShape(999.dp)) {
                    Text("下载", Modifier.padding(horizontal = 24.dp, vertical = 16.dp), color = Ink, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("支持平台：", color = Ink)
                PlatformBadge("YT", Color(0xFFFFEAE6))
                PlatformBadge("B", Color(0xFFE8E9FF))
                PlatformBadge("小红书", Color(0xFFFFF0E6))
            }
        }

        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("正在处理", color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Surface(color = Color(0xFFE9E1D2), shape = RoundedCornerShape(999.dp)) {
                    Text(if (state.workerState == "未入队") "暂无任务" else state.workerState, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color(0xFF8C7D68))
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(color = Color(0xFFFFC6DA), shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.latestVideo?.title ?: "视频下载任务", color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("${state.status} · ${state.progressLabel}", color = Muted)
                    LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)), color = Color(0xFFA57887), trackColor = Color.White.copy(alpha = .5f))
                    if (state.workerState != "未入队") {
                        Surface(onClick = viewModel::cancelTask, color = Color.White.copy(alpha = .55f), shape = RoundedCornerShape(999.dp)) {
                            Text("取消任务", Modifier.padding(horizontal = 16.dp, vertical = 9.dp), color = Ink)
                        }
                    }
                    if (state.loginStatus.isNotBlank()) {
                        Text(state.loginStatus, color = Ink, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Surface(color = Color.White, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("本地导入", color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("通过系统文件选择器导入设备中的视频", color = Muted)
                Surface(onClick = { filePicker.launch(arrayOf("video/*")) }, color = Gold, shape = RoundedCornerShape(999.dp)) {
                    Text("导入视频", Modifier.padding(horizontal = 22.dp, vertical = 14.dp), color = Ink, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PlatformBadge(label: String, color: Color) {
    Box(Modifier.size(if (label.length > 2) 46.dp else 30.dp, 30.dp).clip(RoundedCornerShape(999.dp)).background(color), contentAlignment = Alignment.Center) {
        Text(label, color = Ink, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}
