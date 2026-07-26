@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sublingo.app.ui.transcript

import android.net.Uri
import android.app.Activity
import android.view.LayoutInflater
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.sublingo.app.ui.components.PlaybackSpeedMenu
import com.sublingo.app.ui.components.PlaybackPositionHandoff
import com.sublingo.app.ui.components.SubLingoLogo
import com.sublingo.app.ui.components.VideoScrubber
import com.sublingo.app.ui.components.VideoDoubleTapAction
import com.sublingo.app.ui.components.VideoSeekFeedback
import com.sublingo.app.ui.components.videoDoubleTapAction
import com.sublingo.app.ui.components.isSeekConfirmed
import com.sublingo.app.ui.components.seekConfirmationTimedOut

private val PageBackground = Color(0xFFFDFAF0)
private val Ink = Color(0xFF2E303A)
private val Muted = Color(0xFF747688)
private val Purple = Color(0xFF8B6DF1)
private val Yellow = Color(0xFFF8C62A)
private val HighlightColors = listOf(
    Color(0xFFE8E9FF), Color(0xFFFFC6DA), Color(0xFFFFCDA5), Color(0xFFD7F2DD), Color(0xFFF0EAFF),
)

private data class WordSelection(val sequence: Int, val alignmentId: Int)
private data class SentencePlaybackWindow(val startMs: Long, val endMs: Long, val entered: Boolean = false)

internal const val TRANSCRIPT_FOLLOW_SCROLL_OFFSET_PX = 0

@Composable
fun TranscriptScreen(
    viewModel: TranscriptViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val repositoryState by viewModel.uiState.collectAsState()
    val transitionSnapshot = remember(repositoryState.videoId) {
        TranscriptTransitionHandoff.peek(repositoryState.videoId)
    }
    val transitionState = remember(transitionSnapshot) { transitionSnapshot?.asUiState() }
    val state = transitionState?.takeIf {
        !repositoryState.isLoaded || (it.rows.isNotEmpty() && repositoryState.rows.isEmpty())
    } ?: repositoryState
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var mode by rememberSaveable { mutableStateOf(TranscriptMode.BILINGUAL) }
    var selection by remember { mutableStateOf<WordSelection?>(null) }
    var positionMs by rememberSaveable { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var playbackSpeed by rememberSaveable { mutableFloatStateOf(1f) }
    var speedMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var playerControlsVisible by remember { mutableStateOf(true) }
    var playerInteractionKey by remember { mutableLongStateOf(0L) }
    var seekFeedback by remember { mutableStateOf<VideoDoubleTapAction?>(null) }
    var seekFeedbackKey by remember { mutableLongStateOf(0L) }
    var sentencePlaybackWindow by remember { mutableStateOf<SentencePlaybackWindow?>(null) }
    val player = remember(state.videoId) { ExoPlayer.Builder(context.applicationContext).build() }
    var seeking by remember { mutableStateOf(false) }
    var pendingSeekTarget by remember { mutableStateOf<Long?>(null) }
    var pendingSeekStartedAt by remember { mutableLongStateOf(0L) }
    var reportedSeekPosition by remember { mutableStateOf<Long?>(null) }
    var viewportSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var embeddedPlayerTop by remember { mutableFloatStateOf(0f) }
    var playerReturnProgress by remember { mutableFloatStateOf(0f) }
    var playerReturnJob by remember { mutableStateOf<Job?>(null) }
    var playerReturnStarted by remember { mutableStateOf(false) }
    var playerReturnFinished by remember { mutableStateOf(false) }
    val transitionScope = rememberCoroutineScope()
    val returnDragDistancePx = with(androidx.compose.ui.platform.LocalDensity.current) { 280.dp.toPx() }

    DisposableEffect(transitionSnapshot) {
        onDispose { transitionSnapshot?.let(TranscriptTransitionHandoff::clear) }
    }

    fun finishPlayerReturn() {
        if (playerReturnFinished) return
        playerReturnFinished = true
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        PlaybackPositionHandoff.publish(state.videoId, currentPosition)
        viewModel.savePosition(currentPosition)
        onBack()
    }

    fun settlePlayerReturn(openPlayer: Boolean) {
        if (playerReturnStarted) return
        playerReturnJob?.cancel()
        if (openPlayer) playerReturnStarted = true
        playerReturnJob = transitionScope.launch {
            Animatable(playerReturnProgress).animateTo(
                if (openPlayer) 1f else 0f,
                tween(if (openPlayer) 220 else 200, easing = FastOutSlowInEasing),
            ) { playerReturnProgress = value }
            if (openPlayer) {
                finishPlayerReturn()
            }
        }
    }

    fun togglePlayback(showControls: Boolean = true) {
        sentencePlaybackWindow = null
        if (player.isPlaying) player.pause() else player.play()
        if (showControls) {
            playerControlsVisible = true
            playerInteractionKey++
        }
    }

    fun seekBy(deltaMs: Long, showControls: Boolean = true) {
        sentencePlaybackWindow = null
        val duration = player.duration.takeIf { it > 0L } ?: state.durationMs
        val target = (player.currentPosition + deltaMs).coerceIn(0L, duration.coerceAtLeast(0L))
        positionMs = target
        pendingSeekTarget = target
        pendingSeekStartedAt = android.os.SystemClock.elapsedRealtime()
        reportedSeekPosition = null
        player.seekTo(target)
        if (showControls) {
            playerControlsVisible = true
            playerInteractionKey++
        }
    }

    BackHandler { finishPlayerReturn() }

    LaunchedEffect(state.filePath, state.companionAudioPath) {
        if (!state.isLoaded) return@LaunchedEffect
        val video = state.filePath?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
        if (video == null) {
            playerError = "视频文件不可用"
            return@LaunchedEffect
        }
        playerError = null
        val factory = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
        val videoSource = factory.createMediaSource(MediaItem.fromUri(Uri.fromFile(video)))
        val audio = state.companionAudioPath?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
        if (audio != null && !video.hasAudioTrack()) {
            val audioSource = factory.createMediaSource(MediaItem.fromUri(Uri.fromFile(audio)))
            player.setMediaSource(MergingMediaSource(true, true, videoSource, audioSource))
        } else {
            player.setMediaSource(videoSource)
        }
        player.prepare()
        player.seekTo(state.lastPlayedPositionMs.coerceAtLeast(0L))
        player.playWhenReady = true
        player.play()
    }
    LaunchedEffect(playerControlsVisible, playerInteractionKey, speedMenuExpanded) {
        if (playerControlsVisible && !speedMenuExpanded) {
            delay(3_000)
            playerControlsVisible = false
        }
    }
    LaunchedEffect(seekFeedbackKey) {
        if (seekFeedbackKey == 0L) return@LaunchedEffect
        // 240 ms fully visible + 160 ms component exit = 400 ms total feedback.
        delay(240)
        seekFeedback = null
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
            positionMs = when {
                seeking -> positionMs
                pendingSeekTarget != null -> pendingSeekTarget!!
                else -> current
            }
            playing = player.isPlaying
            val sentenceWindow = sentencePlaybackWindow
            if (sentenceWindow != null && player.isPlaying) {
                if (!sentenceWindow.entered && positionMs in sentenceWindow.startMs until sentenceWindow.endMs) {
                    sentencePlaybackWindow = sentenceWindow.copy(entered = true)
                } else if (shouldPauseSentence(positionMs, sentenceWindow.endMs, sentenceWindow.entered)) {
                    player.pause()
                    sentencePlaybackWindow = null
                    positionMs = sentenceWindow.endMs
                }
            }
            delay(40)
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) reportedSeekPosition = newPosition.positionMs
            }
        }
        player.addListener(listener)
        onDispose {
            viewModel.savePosition(player.currentPosition)
            player.removeListener(listener)
            player.release()
        }
    }

    val currentSequence = state.rows.lastOrNull { positionMs >= it.startMs }?.sequence
    val followTarget = nextTranscriptFollowTarget(state.rows, positionMs, currentSequence)

    val density = androidx.compose.ui.platform.LocalDensity.current
    val configuration = LocalConfiguration.current
    val outerHorizontalPadding = 32.dp
    val outerVerticalPadding = 24.dp
    val immersiveWidthDp = configuration.screenWidthDp.dp
    val embeddedWidthDp = (immersiveWidthDp - outerHorizontalPadding).coerceAtLeast(1.dp)
    val embeddedHeightDp = embeddedWidthDp * 9f / 16f
    val immersiveHeightDp = immersiveWidthDp * 9f / 16f
    val playerWidth = androidx.compose.ui.unit.lerp(embeddedWidthDp, immersiveWidthDp, playerReturnProgress)
    val playerHeight = androidx.compose.ui.unit.lerp(embeddedHeightDp, immersiveHeightDp, playerReturnProgress)
    val currentPlayerHeightPx = with(density) { playerHeight.toPx() }
    val targetPlayerTranslationY = if (viewportSize.height > 0) {
        viewportSize.height / 2f - (embeddedPlayerTop + currentPlayerHeightPx / 2f)
    } else 0f
    val transitionBackground = lerp(PageBackground, Color.Black, playerReturnProgress)
    val activity = context as? Activity
    SideEffect {
        activity?.window?.let { window ->
            window.statusBarColor = transitionBackground.toArgb()
            window.navigationBarColor = transitionBackground.toArgb()
            WindowCompat.getInsetsController(window, window.decorView).apply {
                val useDarkIcons = playerReturnProgress < .45f
                isAppearanceLightStatusBars = useDarkIcons
                isAppearanceLightNavigationBars = useDarkIcons
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
            .graphicsLayer { clip = false }
            .drawBehind {
                val horizontal = outerHorizontalPadding.toPx() / 2f
                val vertical = outerVerticalPadding.toPx() / 2f
                drawRect(
                    color = transitionBackground,
                    topLeft = Offset(-horizontal, -vertical),
                    size = Size(size.width + horizontal * 2f, size.height + vertical * 2f),
                )
            },
    ) {
        Box(Modifier.graphicsLayer { alpha = (1f - playerReturnProgress / .72f).coerceIn(0f, 1f) }) {
            TranscriptHeader(
                title = state.title,
                mode = mode,
                onModeChange = { mode = it },
                onBack = ::finishPlayerReturn,
            )
        }
        Spacer(Modifier.height(10.dp).graphicsLayer { alpha = (1f - playerReturnProgress / .72f).coerceIn(0f, 1f) })
        EmbeddedTranscriptPlayer(
            player = player,
            playing = playing,
            positionMs = positionMs,
            durationMs = player.duration.takeIf { it > 0 } ?: state.durationMs,
            error = playerError,
            controlsVisible = playerControlsVisible,
            playbackSpeed = playbackSpeed,
            onToggleControls = {
                playerControlsVisible = !playerControlsVisible
                playerInteractionKey++
            },
            onTogglePlayback = { togglePlayback() },
            onVideoDoubleTap = { action ->
                when (action) {
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
            onReturnDrag = { delta ->
                playerReturnJob?.cancel()
                if (!playerReturnStarted) {
                    playerReturnProgress = (playerReturnProgress + delta / returnDragDistancePx).coerceIn(0f, 1f)
                }
            },
            onReturnDragEnd = { settlePlayerReturn(playerReturnProgress >= .45f) },
            onReturnDragCancel = { settlePlayerReturn(false) },
            speedMenuExpanded = speedMenuExpanded,
            onSpeedMenuExpandedChange = { speedMenuExpanded = it },
            onSpeedSelected = { selected ->
                playbackSpeed = selected
                player.setPlaybackSpeed(selected)
                playerControlsVisible = true
                playerInteractionKey++
            },
            onSeek = { target ->
                sentencePlaybackWindow = null
                positionMs = target
                pendingSeekTarget = target
                pendingSeekStartedAt = android.os.SystemClock.elapsedRealtime()
                reportedSeekPosition = null
                player.seekTo(target)
            },
            onSeekingChange = { seeking = it },
            onSeekPreview = { positionMs = it },
            seekFeedback = seekFeedback,
            modifier = Modifier
                .requiredWidth(playerWidth)
                .height(playerHeight)
                .align(Alignment.CenterHorizontally)
                .onGloballyPositioned {
                    if (playerReturnProgress == 0f) {
                        embeddedPlayerTop = it.positionInParent().y
                    }
                }
                .graphicsLayer {
                    translationY = targetPlayerTranslationY * playerReturnProgress
                    shape = RoundedCornerShape(30.dp * (1f - playerReturnProgress))
                    clip = playerReturnProgress < 1f
                },
        )
        LaunchedEffect(selection?.sequence) {
            val target = state.rows.indexOfFirst { it.sequence == selection?.sequence }
            if (target >= 0) listState.animateScrollToItem(target, TRANSCRIPT_FOLLOW_SCROLL_OFFSET_PX)
        }
        LaunchedEffect(followTarget?.sequence, playing) {
            if (playing && followTarget != null) {
                listState.scrollToItem(followTarget.rowIndex, TRANSCRIPT_FOLLOW_SCROLL_OFFSET_PX)
            }
        }
        if (state.isLoaded && state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("字幕尚未生成", color = Muted)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = (1f - playerReturnProgress / .72f).coerceIn(0f, 1f) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.rows, key = { it.sequence }) { row ->
                    TranscriptCard(
                        row = row,
                        mode = mode,
                        active = row.sequence == followTarget?.sequence,
                        playing = playing,
                        positionMs = positionMs,
                        selection = selection?.takeIf { it.sequence == row.sequence },
                        onSelect = { alignmentId -> selection = WordSelection(row.sequence, alignmentId) },
                        onSeek = {
                            player.pause()
                            positionMs = row.startMs
                            player.seekTo(0, row.startMs)
                            if (player.playbackState == Player.STATE_IDLE) player.prepare()
                            sentencePlaybackWindow = row.endMs.takeIf { it > row.startMs }
                                ?.let { SentencePlaybackWindow(row.startMs, it) }
                            player.playWhenReady = true
                            player.play()
                            playerControlsVisible = false
                            selection = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptHeader(
    title: String,
    mode: TranscriptMode,
    onModeChange: (TranscriptMode) -> Unit,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onBack, modifier = Modifier.size(42.dp), color = Color.Transparent, shape = RoundedCornerShape(99.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("‹", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Medium) }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                SubLingoLogo(width = 140.dp, height = 49.dp, fontSize = 28.sp)
                Text(title, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.size(42.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TranscriptMode.entries.forEach { item ->
                ModeButton(when (item) { TranscriptMode.ENGLISH -> "英文"; TranscriptMode.CHINESE -> "中文"; TranscriptMode.BILINGUAL -> "双语" }, mode == item) { onModeChange(item) }
            }
        }
    }
}

@Composable
private fun EmbeddedTranscriptPlayer(
    player: ExoPlayer,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    error: String?,
    controlsVisible: Boolean,
    playbackSpeed: Float,
    speedMenuExpanded: Boolean,
    onToggleControls: () -> Unit,
    onTogglePlayback: () -> Unit,
    onVideoDoubleTap: (VideoDoubleTapAction) -> Unit,
    onReturnDrag: (Float) -> Unit,
    onReturnDragEnd: () -> Unit,
    onReturnDragCancel: () -> Unit,
    onSpeedMenuExpandedChange: (Boolean) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekingChange: (Boolean) -> Unit,
    onSeekPreview: (Long) -> Unit,
    seekFeedback: VideoDoubleTapAction?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.clip(RoundedCornerShape(30.dp)).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = {
                (LayoutInflater.from(it).inflate(com.sublingo.app.R.layout.player_view_texture, null, false) as PlayerView).apply {
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        VideoSeekFeedback(
            action = seekFeedback,
            direction = VideoDoubleTapAction.REWIND,
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 28.dp),
        )
        VideoSeekFeedback(
            action = seekFeedback,
            direction = VideoDoubleTapAction.FORWARD,
            modifier = Modifier.align(Alignment.CenterEnd).padding(horizontal = 28.dp),
        )
        // Keep control gestures out of the video tap target. The previous parent-level clickable
        // participated in hit testing above the native SeekBar and could steal its drag sequence.
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onToggleControls() },
                        onDoubleTap = { offset -> onVideoDoubleTap(videoDoubleTapAction(offset.x, size.width.toFloat())) },
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            if (amount > 0f) {
                                change.consume()
                                onReturnDrag(amount)
                            }
                        },
                        onDragEnd = onReturnDragEnd,
                        onDragCancel = onReturnDragCancel,
                    )
                },
        )
        if (error != null) Text(error, color = Color.White, modifier = Modifier.padding(24.dp))
        androidx.compose.animation.AnimatedVisibility(
            visible = controlsVisible,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
        ) {
            Surface(
                onClick = onTogglePlayback,
                modifier = Modifier.size(58.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Yellow,
                shadowElevation = 8.dp,
            ) { Box(contentAlignment = Alignment.Center) { Text(if (playing) "Ⅱ" else "▶", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) } }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = controlsVisible,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    VideoScrubber(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onSeekingChange = onSeekingChange,
                        onPreview = onSeekPreview,
                        onSeek = onSeek,
                        activeColor = Yellow,
                        inactiveColor = Color.White.copy(alpha = .35f),
                        modifier = Modifier.padding(top = 17.dp),
                    )
                    PlaybackSpeedMenu(
                        speed = playbackSpeed,
                        expanded = speedMenuExpanded,
                        onExpandedChange = onSpeedMenuExpandedChange,
                        onSpeedSelected = onSpeedSelected,
                        buttonColor = Color.White,
                        selectedColor = Yellow,
                        compact = true,
                        modifier = Modifier.align(Alignment.TopEnd).offset(y = (-12).dp),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(positionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Text(formatTime(durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

internal fun shouldPauseSentence(positionMs: Long, endMs: Long, entered: Boolean): Boolean =
    entered && positionMs >= endMs

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranscriptCard(
    row: TranscriptRow,
    mode: TranscriptMode,
    active: Boolean,
    playing: Boolean,
    positionMs: Long,
    selection: WordSelection?,
    onSelect: (Int) -> Unit,
    onSeek: () -> Unit,
) {
    val aligned = remember(row.english, row.chinese, row.highlights) {
        TranscriptWordAligner.align(row.english, row.chinese, row.highlights)
    }
    val playColor by animateColorAsState(if (active) Purple else Color(0xFFEDEDFC), tween(220), label = "transcript-play")
    val playIconColor by animateColorAsState(if (active) Color.White else Purple, tween(220), label = "transcript-play-icon")
    val elevation by animateDpAsState(if (active) 4.dp else 1.dp, tween(220), label = "transcript-elevation")
    val playbackAlignmentId = if (active) {
        displayedTranscriptAlignmentId(
            aligned = aligned,
            positionMs = positionMs,
            cueStartMs = row.startMs,
            cueEndMs = row.endMs,
        )
    } else null
    val highlightedAlignmentId = if (playing && active) playbackAlignmentId else selection?.alignmentId ?: playbackAlignmentId
    Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = elevation,
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 17.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(onClick = onSeek, modifier = Modifier.size(34.dp), shape = RoundedCornerShape(99.dp), color = playColor) {
                    Box(contentAlignment = Alignment.Center) { Text("▶", color = playIconColor, fontSize = 12.sp) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatTime(row.startMs), color = Purple, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    if (mode != TranscriptMode.CHINESE) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            aligned.english.forEach { token ->
                                TranscriptToken(
                                    token,
                                    selected = highlightedAlignmentId != null && highlightedAlignmentId in token.alignmentIds,
                                    color = token.alignmentId.highlightColor(),
                                    onSelect = onSelect,
                                    english = true,
                                )
                            }
                        }
                    }
                    if (mode != TranscriptMode.ENGLISH) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            aligned.chinese.forEach { token ->
                                TranscriptToken(
                                    token,
                                    selected = highlightedAlignmentId != null && highlightedAlignmentId in token.alignmentIds,
                                    color = token.alignmentId.highlightColor(),
                                    onSelect = onSelect,
                                    english = false,
                                )
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun TranscriptToken(
    token: DisplayToken,
    selected: Boolean,
    color: Color,
    onSelect: (Int) -> Unit,
    english: Boolean,
) {
    val highlightable = token.alignmentId != null
    // Word highlighting follows playback/selection immediately. The transcript should not
    // fade each token in and out as the active alignment changes between words.
    val tokenBackground = if (selected) color else Color.Transparent
    val stablePadding = Modifier.padding(horizontal = if (token.isWord) 5.dp else 0.dp, vertical = 2.dp)
    val modifier = if (highlightable) Modifier.clickable { onSelect(token.alignmentId!!) } else Modifier
    Surface(
        modifier = modifier,
        color = tokenBackground,
        shape = RoundedCornerShape(9.dp),
    ) {
        Text(
            token.text,
            modifier = stablePadding.heightIn(min = if (english) 29.dp else 26.dp),
            color = if (english) Ink else Muted,
            fontSize = if (english) 17.sp else 14.sp,
            lineHeight = if (english) 25.sp else 22.sp,
            fontWeight = when {
                highlightable && english -> FontWeight.SemiBold
                highlightable -> FontWeight.Medium
                english -> FontWeight.Medium
                else -> FontWeight.Normal
            },
        )
    }
}

private fun Int?.highlightColor(): Color = HighlightColors[kotlin.math.abs(this ?: 0) % HighlightColors.size]

@Composable private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) Yellow else Color.White, shape = RoundedCornerShape(99.dp)) {
        Text(label, Modifier.padding(horizontal = 13.dp, vertical = 7.dp), color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
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
