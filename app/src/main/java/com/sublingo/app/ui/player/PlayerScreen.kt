@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sublingo.app.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.sublingo.app.data.db.SubtitleCueEntity
import com.sublingo.app.ui.components.PlaybackSpeedMenu
import com.sublingo.app.ui.components.PlaybackPositionHandoff
import com.sublingo.app.ui.components.SubLingoLogo
import com.sublingo.app.ui.components.VideoScrubber
import com.sublingo.app.ui.components.VideoDoubleTapAction
import com.sublingo.app.ui.components.VideoSeekFeedback
import com.sublingo.app.ui.components.videoDoubleTapAction
import com.sublingo.app.ui.components.isSeekConfirmed
import com.sublingo.app.ui.components.seekConfirmationTimedOut
import com.sublingo.app.ui.transcript.TranscriptTransitionHandoff
import com.sublingo.app.ui.transcript.TranscriptTransitionSnapshot

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenTranscript: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val playbackPositionUpdate = PlaybackPositionHandoff.peek(state.videoId)
    val context = LocalContext.current
    var savedPosition by rememberSaveable { mutableLongStateOf(state.startPositionMs) }
    var shouldResume by rememberSaveable { mutableStateOf(true) }
    val player = remember(state.videoId) { ExoPlayer.Builder(context.applicationContext).build() }
    var error by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(savedPosition) }
    var duration by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    var pendingSeekTarget by remember { mutableStateOf<Long?>(null) }
    var pendingSeekStartedAt by remember { mutableLongStateOf(0L) }
    var reportedSeekPosition by remember { mutableStateOf<Long?>(null) }
    var playing by remember { mutableStateOf(shouldResume) }
    var speed by remember { mutableFloatStateOf(1f) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionKey by remember { mutableLongStateOf(0L) }
    var seekFeedback by remember { mutableStateOf<VideoDoubleTapAction?>(null) }
    var seekFeedbackKey by remember { mutableLongStateOf(0L) }
    var showEnglish by rememberSaveable { mutableStateOf(true) }
    var showChinese by rememberSaveable { mutableStateOf(true) }
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val fullscreen = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var transcriptTransition by remember { mutableFloatStateOf(0f) }
    var transcriptTransitionJob by remember { mutableStateOf<Job?>(null) }
    var transcriptNavigationStarted by remember { mutableStateOf(false) }
    var transcriptPlayerTargetBounds by remember { mutableStateOf<Rect?>(null) }
    var playerContentBounds by remember { mutableStateOf<Rect?>(null) }
    val transitionScope = rememberCoroutineScope()
    val transcriptDragDistancePx = with(density) { 300.dp.toPx() }
    val measuredTarget = transcriptPlayerTargetBounds
    val transcriptPlayerWidthPx = measuredTarget?.width
        ?.takeIf { it > 0f }
        ?: (viewportSize.width - with(density) { 32.dp.toPx() }).coerceAtLeast(1f)
    val transcriptPlayerTargetCenterY = measuredTarget?.center?.y
        ?.minus(playerContentBounds?.top ?: 0f)
        ?: viewportSize.height / 2f
    val transcriptPlayerTranslationY = if (viewportSize.height > 0) {
        transcriptPlayerTargetCenterY - viewportSize.height / 2f
    } else {
        0f
    }
    val transcriptPlayerTargetScale = if (viewportSize.width > 0) {
        (transcriptPlayerWidthPx / viewportSize.width).coerceIn(.82f, 1f)
    } else {
        .92f
    }
    fun openTranscript() {
        if (transcriptNavigationStarted) return
        transcriptNavigationStarted = true
        viewModel.savePosition(player.currentPosition)
        transcriptTransitionJob?.cancel()
        transcriptTransitionJob = transitionScope.launch {
            Animatable(transcriptTransition).animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = if (transcriptTransition > 0f) 240 else 360,
                    easing = FastOutSlowInEasing,
                ),
            ) { transcriptTransition = value }
            TranscriptTransitionHandoff.publish(
                TranscriptTransitionSnapshot(
                    videoId = state.videoId,
                    title = state.title,
                    filePath = state.filePath,
                    companionAudioPath = state.companionAudioPath,
                    durationMs = duration.takeIf { it > 0L } ?: state.durationMs,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    englishCues = state.englishCues,
                    chineseCues = state.chineseCues,
                ),
            )
            onOpenTranscript(state.videoId)
        }
    }

    fun resetTranscriptTransition() {
        if (transcriptNavigationStarted) return
        transcriptTransitionJob?.cancel()
        transcriptTransitionJob = transitionScope.launch {
            Animatable(transcriptTransition).animateTo(
                targetValue = 0f,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
            ) { transcriptTransition = value }
        }
    }

    fun exitPlayer() {
        viewModel.savePosition(player.currentPosition)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onBack()
    }
    fun togglePlayback(showControls: Boolean = true) {
        if (player.playWhenReady) {
            shouldResume = false
            player.pause()
        } else {
            shouldResume = true
            player.play()
        }
        if (showControls) {
            controlsVisible = true
            interactionKey++
        }
    }
    fun seekBy(deltaMs: Long, showControls: Boolean = true) {
        val upperBound = duration.takeIf { it > 0L } ?: state.durationMs
        val target = (player.currentPosition + deltaMs).coerceIn(0L, upperBound.coerceAtLeast(0L))
        position = target
        savedPosition = target
        pendingSeekTarget = target
        pendingSeekStartedAt = android.os.SystemClock.elapsedRealtime()
        reportedSeekPosition = null
        player.seekTo(target)
        if (showControls) {
            controlsVisible = true
            interactionKey++
        }
    }
    BackHandler { exitPlayer() }

    LaunchedEffect(playing, interactionKey, speedMenuExpanded) {
        if (playing && controlsVisible && !speedMenuExpanded) {
            delay(3_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(seekFeedbackKey) {
        if (seekFeedbackKey == 0L) return@LaunchedEffect
        // 240 ms fully visible + 160 ms component exit = 400 ms total feedback.
        delay(240)
        seekFeedback = null
    }

    LaunchedEffect(state.filePath, state.companionAudioPath) {
        val file = state.filePath?.let(::File)
        if (file == null || !file.isFile || file.length() == 0L) {
            error = "本地视频文件不存在或为空"
            return@LaunchedEffect
        }
        error = null
        val mediaSourceFactory = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
        val videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(Uri.fromFile(file)))
        val companionAudio = state.companionAudioPath?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
        if (companionAudio != null && !file.hasAudioTrack()) {
            val audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(Uri.fromFile(companionAudio)))
            player.setMediaSource(MergingMediaSource(true, true, videoSource, audioSource))
        } else {
            player.setMediaSource(videoSource)
        }
        savedPosition = if (savedPosition > 0) savedPosition else state.startPositionMs
        player.seekTo(savedPosition)
        shouldResume = true
        playing = true
        player.playWhenReady = true
        player.prepare()
    }
    LaunchedEffect(player, playbackPositionUpdate) {
        playbackPositionUpdate?.let { update ->
            transcriptTransitionJob?.cancel()
            transcriptTransition = 0f
            transcriptNavigationStarted = false
            savedPosition = update.positionMs
            position = update.positionMs
            pendingSeekTarget = update.positionMs
            pendingSeekStartedAt = android.os.SystemClock.elapsedRealtime()
            reportedSeekPosition = null
            player.seekTo(update.positionMs)
            PlaybackPositionHandoff.consume(update)
        }
    }
    LaunchedEffect(player) {
        while (true) {
            val current = player.currentPosition.coerceAtLeast(0L)
            val target = pendingSeekTarget
            if (target != null && (
                    isSeekConfirmed(current, target, reportedSeekPosition) ||
                        seekConfirmationTimedOut(android.os.SystemClock.elapsedRealtime(), pendingSeekStartedAt)
                    )) {
                pendingSeekTarget = null
                reportedSeekPosition = null
            }
            if (!seeking && pendingSeekTarget == null) {
                position = current
                savedPosition = position
            } else if (pendingSeekTarget != null) {
                position = pendingSeekTarget!!
            }
            duration = player.duration.takeIf { it > 0L } ?: state.durationMs
            playing = player.isPlaying
            shouldResume = player.playWhenReady
            delay(500)
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) reportedSeekPosition = newPosition.positionMs
            }
            override fun onPlayerError(playbackError: PlaybackException) {
                error = "视频播放失败：${playbackError.errorCodeName}"
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && shouldResume && !player.isPlaying) {
                    player.play()
                }
            }
        }
        player.addListener(listener)
        onDispose { viewModel.savePosition(player.currentPosition); player.removeListener(listener); player.release() }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
            .onGloballyPositioned { playerContentBounds = it.boundsInRoot() }
            .background(if (fullscreen) Color.Black else Color(0xFFFDFAF0))
            .pointerInput(state.videoId, duration) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        interactionKey++
                    },
                    onDoubleTap = { offset ->
                        when (videoDoubleTapAction(offset.x, size.width.toFloat())) {
                            VideoDoubleTapAction.REWIND -> {
                                seekBy(-10_000L, showControls = false)
                                seekFeedback = VideoDoubleTapAction.REWIND
                                seekFeedbackKey++
                            }
                            VideoDoubleTapAction.TOGGLE_PLAYBACK -> togglePlayback(showControls = false)
                            VideoDoubleTapAction.FORWARD -> {
                                seekBy(10_000L, showControls = false)
                                seekFeedback = VideoDoubleTapAction.FORWARD
                                seekFeedbackKey++
                            }
                        }
                    },
                )
            }
            .pointerInput(state.videoId, fullscreen, transcriptDragDistancePx) {
            if (!fullscreen) detectVerticalDragGestures(
                onDragStart = { transcriptTransitionJob?.cancel() },
                onVerticalDrag = { change, amount ->
                    change.consume()
                    if (!transcriptNavigationStarted) {
                        transcriptTransition = (transcriptTransition - amount / transcriptDragDistancePx).coerceIn(0f, 1f)
                    }
                },
                onDragEnd = {
                    if (transcriptTransition >= .5f) openTranscript() else resetTranscriptTransition()
                },
                onDragCancel = ::resetTranscriptTransition,
            )
        },
    ) {
        if (!fullscreen) {
            TranscriptTransitionPreview(
                title = state.title,
                englishCues = state.englishCues,
                chineseCues = state.chineseCues,
                positionMs = position,
                onPlayerBounds = { transcriptPlayerTargetBounds = it },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (1f - transcriptTransition).coerceIn(0f, 1f))),
            )
        }
        AndroidView(
            factory = {
                (LayoutInflater.from(it).inflate(com.sublingo.app.R.layout.player_view_texture, null, false) as PlayerView).apply {
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = if (fullscreen) Modifier.fillMaxSize() else Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .align(Alignment.Center)
                .graphicsLayer {
                    val progress = transcriptTransition
                    translationY = transcriptPlayerTranslationY * progress
                    scaleX = 1f + (transcriptPlayerTargetScale - 1f) * progress
                    scaleY = scaleX
                    shape = RoundedCornerShape(30.dp * progress)
                    clip = progress > 0f
                },
        )
        VideoSeekFeedback(
            action = seekFeedback,
            direction = VideoDoubleTapAction.REWIND,
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 42.dp),
        )
        VideoSeekFeedback(
            action = seekFeedback,
            direction = VideoDoubleTapAction.FORWARD,
            modifier = Modifier.align(Alignment.CenterEnd).padding(horizontal = 42.dp),
        )
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).graphicsLayer {
                alpha = (1f - transcriptTransition / .32f).coerceIn(0f, 1f)
            },
        ) {
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .42f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleAction("‹", ::exitPlayer)
                Text(state.title, Modifier.weight(1f).padding(horizontal = 14.dp), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Surface(onClick = ::openTranscript, shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = .16f)) {
                    Text("逐字稿", Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        error?.let {
            Surface(Modifier.align(Alignment.Center).padding(24.dp), color = Color(0xDD2E303A), shape = RoundedCornerShape(24.dp)) {
                Text(it, Modifier.padding(24.dp), color = Color.White)
            }
        }
        val englishCue = state.englishCues.activeAt(position)
        val chineseCue = state.chineseCues.activeAt(position)
        if ((showEnglish && englishCue != null) || (showChinese && chineseCue != null)) {
            Surface(
                onClick = { (englishCue ?: chineseCue)?.let { player.seekTo(it.startMs) } },
                modifier = if (fullscreen) {
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 32.dp)
                        .graphicsLayer { alpha = (1f - transcriptTransition / .32f).coerceIn(0f, 1f) }
                } else {
                    Modifier.align(Alignment.Center).padding(horizontal = 24.dp).offset(y = 150.dp)
                        .graphicsLayer { alpha = (1f - transcriptTransition / .32f).coerceIn(0f, 1f) }
                },
                color = Color.Black.copy(alpha = .68f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (showEnglish) englishCue?.let { Text(it.text, color = Color.White, fontWeight = FontWeight.Bold) }
                    if (showChinese) chineseCue?.let { Text(it.text, color = Color(0xFFFFE083), modifier = Modifier.padding(top = if (showEnglish && englishCue != null) 4.dp else 0.dp)) }
                }
            }
        }
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer {
                alpha = (1f - transcriptTransition / .32f).coerceIn(0f, 1f)
            },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                shape = RoundedCornerShape(27.dp), color = Color.White.copy(alpha = .88f), shadowElevation = 12.dp,
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeLabel(position)
                    VideoScrubber(
                        positionMs = position,
                        durationMs = duration,
                        onSeekingChange = { seeking = it },
                        onPreview = { position = it },
                        onSeek = {
                            position = it
                            savedPosition = it
                            pendingSeekTarget = it
                            pendingSeekStartedAt = android.os.SystemClock.elapsedRealtime()
                            reportedSeekPosition = null
                            player.seekTo(it)
                            controlsVisible = true
                            interactionKey++
                        },
                        activeColor = Color(0xFFFDCF44),
                        inactiveColor = Color(0x222E303A),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    TimeLabel(duration)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubtitleToggle("EN", showEnglish) { showEnglish = !showEnglish }
                        SubtitleToggle("中", showChinese) { showChinese = !showChinese }
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        onClick = { togglePlayback() },
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFFDCF44),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (playing) "Ⅱ" else "▶", color = Color(0xFF2D2D44), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    PlaybackSpeedMenu(
                        speed = speed,
                        expanded = speedMenuExpanded,
                        onExpandedChange = { speedMenuExpanded = it },
                        onSpeedSelected = { selected ->
                            speed = selected
                            player.setPlaybackSpeed(selected)
                            controlsVisible = true
                            interactionKey++
                        },
                        buttonColor = Color(0xFF2D2D44),
                        compact = true,
                    )
                    Surface(
                        onClick = {
                            savedPosition = player.currentPosition.coerceAtLeast(position)
                            shouldResume = player.playWhenReady
                            viewModel.savePosition(savedPosition)
                            activity?.requestedOrientation = if (fullscreen) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                            controlsVisible = true
                            interactionKey++
                        },
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Transparent,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (fullscreen) "↙" else "⛶", color = Color(0xFF2D2D44), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun TranscriptTransitionPreview(
    title: String,
    englishCues: List<SubtitleCueEntity>,
    chineseCues: List<SubtitleCueEntity>,
    positionMs: Long,
    onPlayerBounds: (Rect) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val chineseBySequence = remember(chineseCues) { chineseCues.associateBy { it.sequence } }
    val previewRows = remember(englishCues, chineseCues, positionMs) {
        val start = englishCues.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
        englishCues.drop(start).take(2).map { it to chineseBySequence[it.sequence] }
    }
    Column(modifier.background(Color(0xFFFDFAF0)).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Text("‹", color = Color(0xFF2E303A), fontSize = 34.sp, fontWeight = FontWeight.Medium)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                SubLingoLogo(width = 140.dp, height = 49.dp, fontSize = 28.sp)
                Text(title, color = Color(0xFF747688), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
            }
            Spacer(Modifier.size(42.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Spacer(Modifier.weight(1f))
            listOf("英文", "中文", "双语").forEach { label ->
                Surface(color = if (label == "双语") Color(0xFFF8C62A) else Color.White, shape = RoundedCornerShape(99.dp)) {
                    Text(label, Modifier.padding(horizontal = 13.dp, vertical = 7.dp), color = Color(0xFF2E303A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onGloballyPositioned { onPlayerBounds(it.boundsInRoot()) }
                .background(Color.Black, RoundedCornerShape(30.dp)),
        )
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            previewRows.forEach { (english, chinese) ->
                Surface(color = Color.White, shape = RoundedCornerShape(28.dp), shadowElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 17.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(modifier = Modifier.size(34.dp), shape = CircleShape, color = Color(0xFFEDEDFC)) {
                            Box(contentAlignment = Alignment.Center) { Text("▶", color = Color(0xFF8B6DF1), fontSize = 12.sp) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(formatPreviewTime(english.startMs), color = Color(0xFF8B6DF1), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(english.text, color = Color(0xFF2E303A), fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 25.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            chinese?.let { Text(it.text, color = Color(0xFF747688), fontSize = 14.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}

private fun formatPreviewTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

private fun File.hasAudioTrack(): Boolean {
    val extractor = android.media.MediaExtractor()
    return try {
        extractor.setDataSource(absolutePath)
        (0 until extractor.trackCount).any { index ->
            extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }
    } catch (_: Throwable) {
        false
    } finally {
        extractor.release()
    }
}

private fun List<SubtitleCueEntity>.activeAt(positionMs: Long): SubtitleCueEntity? {
    var low = 0
    var high = lastIndex
    while (low <= high) {
        val middle = (low + high).ushr(1)
        val cue = this[middle]
        when {
            positionMs < cue.startMs -> high = middle - 1
            positionMs >= cue.endMs -> low = middle + 1
            else -> return cue
        }
    }
    return null
}

@Composable private fun SubtitleToggle(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (enabled) Color(0xFFFDCF44) else Color(0x222E303A), shape = RoundedCornerShape(999.dp)) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color(0xFF2D2D44), fontWeight = FontWeight.Bold)
    }
}

@Composable private fun CircleAction(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(42.dp), shape = RoundedCornerShape(999.dp), color = Color.Black.copy(alpha = .45f)) {
        Box(contentAlignment = Alignment.Center) { Text(label, color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun TimeLabel(milliseconds: Long) {
    val total = milliseconds.coerceAtLeast(0L) / 1000
    Text("%02d:%02d".format(total / 60, total % 60), color = Color(0xFF2D2D44), fontWeight = FontWeight.Bold)
}
