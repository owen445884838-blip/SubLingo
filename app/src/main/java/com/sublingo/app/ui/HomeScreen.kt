package com.sublingo.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sublingo.app.data.db.VideoEntity
import com.sublingo.app.R
import coil.compose.AsyncImage
import com.sublingo.app.domain.model.ProcessingStage
import com.sublingo.app.domain.model.ProcessingState
import com.sublingo.app.ui.components.PillInput
import com.sublingo.app.ui.components.SubLingoLogo
import com.sublingo.app.ui.download.YoutubeLoginDialog
import java.io.File

private val HomeCream = Color(0xFFFDFAF0)
private val HomeInk = Color(0xFF2E303A)
private val HomeMuted = Color(0xFF747688)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenLibrary: () -> Unit = {},
    onOpenVideo: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val localVideoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importLocal)
    }
    if (state.youtubeLoginRequired) {
        YoutubeLoginDialog(
            targetVideoUrl = state.youtubeLoginUrl,
            onAuthenticated = viewModel::completeYoutubeLogin,
            onDismiss = viewModel::dismissYoutubeLogin,
        )
    }
    var importOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var importOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize().background(HomeCream)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 34.dp, bottom = 188.dp),
            verticalArrangement = Arrangement.spacedBy(38.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SubLingoLogo()
                }
            }
            item { NewTaskSection(state.url, viewModel::updateUrl, viewModel::createTask) }
            if (state.activeTasks.isNotEmpty()) {
                item {
                    ProcessingSection(
                        state.activeTasks,
                        state.taskActionStatus,
                        viewModel::retrySubtitlePipeline,
                        viewModel::requestYoutubeLogin,
                        viewModel::refreshYoutubeSession,
                        viewModel::cancelTask,
                    )
                }
            }
            item {
                RecentVideosSection(
                    videos = state.recentVideos,
                    activeTasks = state.activeTasks,
                    selectionMode = state.selectionMode,
                    selectedIds = state.selectedVideoIds,
                    onToggleSelectionMode = viewModel::toggleSelectionMode,
                    onToggleVideo = viewModel::toggleVideoSelection,
                    onDelete = viewModel::deleteSelectedVideos,
                    onOpenVideo = onOpenVideo,
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 8.dp)
                .offset { androidx.compose.ui.unit.IntOffset(importOffsetX.toInt(), importOffsetY.toInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        importOffsetX = (importOffsetX + dragAmount.x).coerceIn(-size.width.toFloat() * 4f, 0f)
                        importOffsetY = (importOffsetY + dragAmount.y).coerceIn(-size.height.toFloat() * 8f, 10.dp.toPx())
                    }
                },
            horizontalAlignment = Alignment.End,
        ) {
            Surface(
                onClick = { localVideoPicker.launch(arrayOf("video/*")) },
                modifier = Modifier.size(54.dp),
                color = Color(0xFFF8C62A),
                shape = RoundedCornerShape(999.dp),
                shadowElevation = 10.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("＋", color = HomeInk, fontSize = 31.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NewTaskSection(url: String, onUrlChange: (String) -> Unit, onDownload: () -> Unit) {
    Column {
        Text("新学习任务", color = HomeInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(Color(0xFF8B6DF1)).padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFFF0EAFF)).padding(start = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PillInput(value = url, onValueChange = onUrlChange, placeholder = "在此粘贴视频链接...", modifier = Modifier.weight(1f))
                if (url.isNotEmpty()) {
                    Surface(onClick = { onUrlChange("") }, color = HomeInk.copy(alpha = .1f), shape = RoundedCornerShape(999.dp), modifier = Modifier.size(30.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("×", color = HomeInk.copy(alpha = .7f), fontSize = 18.sp) }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(onClick = onDownload, shape = RoundedCornerShape(999.dp), color = Color(0xFFF8C62A)) {
                Text("下载", Modifier.padding(horizontal = 24.dp, vertical = 16.dp), color = HomeInk, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("支持平台：", color = HomeInk)
            PlatformDot(R.drawable.platform_youtube, Color(0xFFFFEAE6), "YouTube")
            PlatformDot(R.drawable.platform_bilibili, Color(0xFFFFEAE6), "Bilibili")
            PlatformDot(R.drawable.platform_tiktok, Color(0xFFE8E9FF), "TikTok")
        }
    }
}

@Composable
private fun ProcessingSection(
    tasks: List<HomeTaskUi>,
    taskActionStatus: Map<String, String>,
    onRetrySubtitle: (String) -> Unit,
    onYoutubeLogin: (HomeTaskUi) -> Unit,
    onRefreshYoutube: (HomeTaskUi) -> Unit,
    onCancelTask: (HomeTaskUi) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("正在处理", color = HomeInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Surface(color = Color(0xFFE9E1D2), shape = RoundedCornerShape(999.dp)) {
                Text("${tasks.size} 处理中", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color(0xFF8C7D68))
            }
        }
        Spacer(Modifier.height(14.dp))
        tasks.forEachIndexed { index, task ->
            val pink = index % 2 == 0
            val cardGrowth = remember(task.job.videoId) { Animatable(.06f) }
            val contentAlpha = remember(task.job.videoId) { Animatable(0f) }
            LaunchedEffect(task.job.videoId) {
                cardGrowth.animateTo(1f, tween(440, easing = FastOutSlowInEasing))
                contentAlpha.animateTo(1f, tween(220))
            }
            Surface(
                color = if (pink) Color(0xFFFFC6DA) else Color(0xFFFFCDA5),
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    scaleX = cardGrowth.value
                    scaleY = cardGrowth.value
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            ) {
                Column(Modifier.padding(22.dp).graphicsLayer { alpha = contentAlpha.value }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(task.title, Modifier.weight(1f), color = HomeInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Surface(
                            onClick = { onCancelTask(task) },
                            modifier = Modifier.size(30.dp),
                            color = Color.White.copy(alpha = .64f),
                            shape = RoundedCornerShape(99.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("×", color = HomeInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(taskStatusLabel(task), color = HomeInk)
                    task.job.lastErrorMessage?.takeIf { task.job.state in setOf(ProcessingState.FAILED, ProcessingState.WAITING_FOR_USER) }?.let { message ->
                        Spacer(Modifier.height(6.dp))
                        Text(message, color = Color(0xFF7A2731), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(18.dp))
                    if (task.job.state in setOf(ProcessingState.PENDING, ProcessingState.RUNNING)) {
                        LinearProgressIndicator(progress = { task.job.progress / 100f }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)), color = if (pink) Color(0xFFA57887) else Color(0xFFA87E5E), trackColor = Color.White.copy(alpha = .55f))
                    } else if (task.job.state == ProcessingState.WAITING_FOR_USER &&
                        task.job.lastErrorCode == "YOUTUBE_LOGIN_REQUIRED"
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(onClick = { onYoutubeLogin(task) }, color = Color.White.copy(alpha = .78f), shape = RoundedCornerShape(999.dp)) {
                                Text("登录 YouTube 并继续", Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = HomeInk, fontWeight = FontWeight.Bold)
                            }
                            Surface(
                                onClick = { onRefreshYoutube(task) },
                                modifier = Modifier.size(42.dp),
                                color = Color.White.copy(alpha = .78f),
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("↻", color = HomeInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else if (task.hasMedia) {
                        Surface(onClick = { onRetrySubtitle(task.job.videoId) }, color = Color.White.copy(alpha = .72f), shape = RoundedCornerShape(999.dp)) {
                            Text("重试字幕处理", Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = HomeInk, fontWeight = FontWeight.Bold)
                        }
                    }
                    taskActionStatus[task.job.videoId]?.let { status ->
                        Spacer(Modifier.height(8.dp))
                        Text(status, color = HomeInk, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (index != tasks.lastIndex) Spacer(Modifier.height(14.dp))
        }
    }
}

private fun taskStatusLabel(task: HomeTaskUi): String = when (task.job.state) {
    ProcessingState.WAITING_FOR_USER -> if (task.job.lastErrorCode == "YOUTUBE_LOGIN_REQUIRED") "等待登录 YouTube" else "等待配置"
    ProcessingState.FAILED -> "处理失败"
    ProcessingState.CANCELLED -> "已取消"
    ProcessingState.SUCCEEDED -> if (task.job.currentStage == ProcessingStage.VOCABULARY) "生词与逐字稿已完成" else "正在继续学习内容处理"
    ProcessingState.PENDING -> task.downloadQueuePosition?.let { "下载队列第 $it 位 · 等待联网或执行" }
        ?: "${stageLabel(task.job.currentStage)} · 等待执行"
    ProcessingState.RUNNING -> "${stageLabel(task.job.currentStage)}... ${task.job.progress}%"
}

private fun stageLabel(stage: ProcessingStage) = when (stage) {
    ProcessingStage.METADATA -> "正在获取视频信息"
    ProcessingStage.DOWNLOAD -> "正在下载视频"
    ProcessingStage.SUBTITLE_DISCOVERY -> "正在准备语音转录"
    ProcessingStage.AUDIO_EXTRACTION -> "正在提取音频"
    ProcessingStage.TRANSCRIPTION -> "正在转录字幕"
    ProcessingStage.TRANSLATION -> "正在翻译字幕"
    ProcessingStage.VOCABULARY -> "正在生成生词"
}

@Composable
private fun RecentVideosSection(
    videos: List<VideoEntity>,
    activeTasks: List<HomeTaskUi>,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelectionMode: () -> Unit,
    onToggleVideo: (String) -> Unit,
    onDelete: () -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("最近视频", color = HomeInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (videos.isNotEmpty()) {
                Text("${videos.size}", Modifier.padding(start = 10.dp), color = HomeMuted, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            if (selectionMode && selectedIds.isNotEmpty()) {
                Surface(onClick = onDelete, color = Color.Transparent) { Text("删除(${selectedIds.size})", Modifier.padding(8.dp), color = Color(0xFFBA1A1A), fontWeight = FontWeight.Bold) }
            }
            Surface(onClick = onToggleSelectionMode, color = Color.Transparent) {
                Text(if (selectionMode) "取消" else "选择", Modifier.padding(8.dp), color = HomeMuted)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (videos.isEmpty()) Text("下载完成的视频会显示在这里", color = HomeMuted)
        else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            videos.chunked(2).forEach { rowVideos ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    rowVideos.forEach { video ->
                        VideoCard(video, activeTasks.any { it.job.videoId == video.id }, Modifier.weight(1f), selectionMode, video.id in selectedIds) {
                            if (selectionMode) onToggleVideo(video.id) else onOpenVideo(video.id)
                        }
                    }
                    if (rowVideos.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun VideoCard(video: VideoEntity, processing: Boolean, modifier: Modifier, selectionMode: Boolean, selected: Boolean, onClick: () -> Unit) {
    val cardGrowth = remember(video.id) { Animatable(if (processing) .06f else 1f) }
    val contentAlpha = remember(video.id) { Animatable(if (processing) 0f else 1f) }
    LaunchedEffect(video.id) {
        if (processing) {
            cardGrowth.animateTo(1f, tween(440, easing = FastOutSlowInEasing))
            contentAlpha.animateTo(1f, tween(220))
        }
    }
    val rotation = if (processing) {
        rememberInfiniteTransition(label = "video-processing").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "rotation").value
    } else 0f
    Surface(
        onClick = onClick,
        enabled = !processing || selectionMode,
        modifier = modifier.graphicsLayer {
            scaleX = cardGrowth.value
            scaleY = cardGrowth.value
            transformOrigin = TransformOrigin(0f, 0f)
        },
        shape = RoundedCornerShape(28.dp),
        color = if (selected) Color(0xFFE7DEFF) else Color.White,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(8.dp).graphicsLayer { alpha = contentAlpha.value }) {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(22.dp)).background(Color(0xFFE6E0EC))) {
                AnimatedContent(
                    targetState = video.thumbnail,
                    label = "video-thumbnail",
                ) { thumbnail ->
                    if (!thumbnail.isNullOrBlank()) {
                        AsyncImage(
                            model = File(thumbnail),
                            contentDescription = video.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    } else if (!processing) {
                        Text("▶", modifier = Modifier.align(Alignment.Center), color = Color(0xFF8B6DF1), style = MaterialTheme.typography.headlineLarge)
                    }
                }
                if (processing || selectionMode) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = if (processing) Color.White.copy(alpha = .92f) else if (selected) Color(0xFF7656D6) else Color.White.copy(alpha = .9f),
                    ) {
                        if (processing) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotation },
                                strokeWidth = 2.dp,
                                color = Color(0xFF8B6DF1),
                            )
                        } else {
                            Text(
                                if (selected) "✓" else "○",
                                Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                color = if (selected) Color.White else HomeInk,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            AnimatedContent(
                targetState = video.title ?: video.filePath?.substringAfterLast('/') ?: "下载中…",
                label = "video-title",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            ) { title ->
                Text(title, color = HomeInk, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PlatformDot(iconRes: Int, background: Color, contentDescription: String) {
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(999.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
    }
}
