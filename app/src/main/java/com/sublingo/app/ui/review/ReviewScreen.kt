package com.sublingo.app.ui.review

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sublingo.app.data.db.ReviewStudyCardRow
import com.sublingo.app.data.vocabulary.ContextualChineseMeaningResolver
import com.sublingo.app.data.review.DailyReviewStats
import com.sublingo.app.data.review.ReviewRating
import com.sublingo.app.data.review.ReviewStatsAggregator
import com.sublingo.app.data.vocabulary.VocabularyDifficulty
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val Cream = Color(0xFFFDFAF0)
private val Ink = Color(0xFF191B24)
private val Muted = Color(0xFF747688)
private val Green = Color(0xFF00D084)
private val Red = Color(0xFFEB5757)
private val Blue = Color(0xFF2F80ED)
private val Yellow = Color(0xFFF2C94C)
private val Orange = Color(0xFFFF8A00)
private val Purple = Color(0xFFBB6BD9)
private val Border = Color(0xFFE2E1EF)
private val StudyCardTopGap = 12.dp
// The bottom navigation casts its shadow upward, so four extra dp are needed for the
// visible card-to-pill gap to match the gap below the difficulty selector. This also
// keeps the rotated card corners inside the route canvas while a swipe is in progress.
private val StudyCardBottomGap = 16.dp

@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel = hiltViewModel(),
    onOpenSource: (String, Long) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsState()
    Column(Modifier.fillMaxSize().background(Cream)) {
        ReviewHeader(state, viewModel::selectSection)
        DifficultySelector(
            difficulty = state.difficulty,
            filteredOutCount = state.filteredOutCount,
            onDifficulty = viewModel::setDifficulty,
            reviewScope = state.reviewScope.takeIf { state.section == ReviewSection.STUDY },
            wordBooks = state.wordBooks,
            favoriteCount = state.allCards.count { it.isFavorite },
            onReviewScope = viewModel::selectReviewScope,
        )
        when (state.section) {
            ReviewSection.STUDY -> StudyContent(state, viewModel, onOpenSource, Modifier.weight(1f))
            ReviewSection.WORDS -> WordBookContent(state, viewModel, Modifier.weight(1f))
            ReviewSection.STATS -> StatsContent(state, viewModel, Modifier.weight(1f))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DifficultySelector(
    difficulty: VocabularyDifficulty,
    filteredOutCount: Int,
    onDifficulty: (VocabularyDifficulty) -> Unit,
    reviewScope: ReviewScope?,
    wordBooks: List<ReviewWordBook>,
    favoriteCount: Int,
    onReviewScope: (ReviewScope) -> Unit,
) {
    val levels = VocabularyDifficulty.selectable
    val index = levels.indexOf(difficulty).coerceAtLeast(0)
    var expanded by rememberSaveable { mutableStateOf(false) }
    var previewIndex by rememberSaveable(difficulty) { mutableFloatStateOf(index.toFloat()) }
    var scopeExpanded by remember { mutableStateOf(false) }
    val previewDifficulty = levels[previewIndex.roundToInt().coerceIn(levels.indices)]
    val color by animateColorAsState(difficultyColor(if (expanded) previewDifficulty else difficulty), label = "difficultyColor")
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(25.dp),
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = if (expanded) 11.dp else 7.dp)) {
            if (reviewScope != null) {
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { scopeExpanded = true }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("复习范围", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            reviewScope.label,
                            Modifier.weight(1f).padding(horizontal = 10.dp),
                            color = Ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                        )
                        Text("▾", color = Muted, fontSize = 12.sp)
                    }
                    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surface = Color.White, onSurface = Ink)) {
                        DropdownMenu(
                            expanded = scopeExpanded,
                            onDismissRequest = { scopeExpanded = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(Color.White, RoundedCornerShape(22.dp))
                                .border(1.dp, Border, RoundedCornerShape(22.dp)),
                            shape = RoundedCornerShape(22.dp),
                            containerColor = Color.White,
                            tonalElevation = 0.dp,
                            shadowElevation = 5.dp,
                        ) {
                            DropdownMenuItem(
                                text = { Text("全部单词", color = Ink, fontWeight = if (reviewScope.sourceVideoId == null && !reviewScope.favoritesOnly) FontWeight.Black else FontWeight.Normal) },
                                onClick = { scopeExpanded = false; onReviewScope(ReviewScope()) },
                                trailingIcon = { if (reviewScope.sourceVideoId == null && !reviewScope.favoritesOnly) Text("✓", color = Green) },
                            )
                            DropdownMenuItem(
                                text = { Column { Text("我的收藏", color = Ink, fontWeight = if (reviewScope.favoritesOnly) FontWeight.Black else FontWeight.Normal); Text("$favoriteCount 张卡片", color = Muted, fontSize = 11.sp) } },
                                onClick = { scopeExpanded = false; onReviewScope(ReviewScope(label = "我的收藏", favoritesOnly = true)) },
                                trailingIcon = { if (reviewScope.favoritesOnly) Text("★", color = Yellow) },
                            )
                            wordBooks.forEach { book ->
                                DropdownMenuItem(
                                    text = { Column { Text(book.title, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${book.cardCount} 张卡片", color = Muted, fontSize = 11.sp) } },
                                    onClick = { scopeExpanded = false; onReviewScope(ReviewScope(book.sourceVideoId, book.title)) },
                                    trailingIcon = { if (reviewScope.sourceVideoId == book.sourceVideoId) Text("✓", color = Green) },
                                )
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                    previewIndex = index.toFloat()
                    expanded = !expanded
                }.padding(top = if (reviewScope != null) 7.dp else 1.dp, bottom = if (expanded) 3.dp else 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "复习难度  ·  ${if (expanded) previewDifficulty.label else difficulty.label} · ${if (expanded) previewDifficulty.cefrLabel else difficulty.cefrLabel}",
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Box(Modifier.size(11.dp).background(color, CircleShape))
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column {
                    Text(
                        if (filteredOutCount > 0) "${previewDifficulty.description} · 已过滤 $filteredOutCount 个基础词" else previewDifficulty.description,
                        Modifier.padding(top = 7.dp), color = Muted, fontSize = 12.sp,
                    )
                    Slider(
                        value = previewIndex,
                        onValueChange = { previewIndex = it },
                        onValueChangeFinished = {
                            onDifficulty(levels[previewIndex.roundToInt().coerceIn(levels.indices)])
                            expanded = false
                        },
                        valueRange = 0f..levels.lastIndex.toFloat(),
                        steps = levels.size - 2,
                        thumb = {
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .shadow(5.dp, CircleShape, clip = false)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(1.dp, Color(0xFFE1E1E5), CircleShape),
                            )
                        },
                        track = { sliderState ->
                            Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                                val radius = size.height / 2f
                                drawRoundRect(
                                    color = Color(0xFFE5E5E8),
                                    size = size,
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                                )
                                val fraction = ((sliderState.value - sliderState.valueRange.start) /
                                    (sliderState.valueRange.endInclusive - sliderState.valueRange.start)).coerceIn(0f, 1f)
                                if (fraction > 0f) {
                                    drawRoundRect(
                                        color = color,
                                        size = Size(size.width * fraction, size.height),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                                    )
                                }
                                levels.indices.forEach { tick ->
                                    val x = size.width * tick / levels.lastIndex
                                    drawCircle(
                                        color = if (tick <= previewIndex.roundToInt()) Color.White.copy(alpha = .55f) else Color(0xFFA9A9AD),
                                        radius = 3.dp.toPx(),
                                        center = Offset(x.coerceIn(3.dp.toPx(), size.width - 3.dp.toPx()), size.height / 2f),
                                    )
                                }
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Transparent,
                            activeTrackColor = color,
                            inactiveTrackColor = Color(0xFFE5E5E8),
                            activeTickColor = Color.White.copy(alpha = .55f),
                            inactiveTickColor = Color(0xFFA9A9AD),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .layout { measurable, constraints ->
                                val extraWidth = 24.dp.roundToPx()
                                val expandedWidth = constraints.maxWidth + extraWidth
                                val placeable = measurable.measure(
                                    constraints.copy(minWidth = expandedWidth, maxWidth = expandedWidth),
                                )
                                layout(constraints.maxWidth, placeable.height) {
                                    placeable.place(-extraWidth / 2, 0)
                                }
                            }
                            .semantics {
                                contentDescription = "英语难度：${previewDifficulty.label}，${previewDifficulty.cefrLabel}"
                            },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        levels.forEach { level ->
                            Text(level.label, color = if (level == previewDifficulty) color else Muted, fontSize = 10.sp, fontWeight = if (level == previewDifficulty) FontWeight.Black else FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

private fun difficultyColor(difficulty: VocabularyDifficulty): Color = when (difficulty) {
    VocabularyDifficulty.A1 -> Color(0xFF19B978)
    VocabularyDifficulty.A2 -> Color(0xFF16B8B1)
    VocabularyDifficulty.B1 -> Color(0xFF3D9BF2)
    VocabularyDifficulty.B2 -> Color(0xFF8D68D8)
    VocabularyDifficulty.C1, VocabularyDifficulty.C2 -> Color(0xFFF08A3C)
    VocabularyDifficulty.UNKNOWN -> Muted
}

@Composable
private fun ReviewHeader(state: ReviewUiState, onSection: (ReviewSection) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SectionPill("学习", state.section == ReviewSection.STUDY) { onSection(ReviewSection.STUDY) }
                SectionPill("生词本", state.section == ReviewSection.WORDS) { onSection(ReviewSection.WORDS) }
                SectionPill("数据", state.section == ReviewSection.STATS) { onSection(ReviewSection.STATS) }
            }
            if (state.section == ReviewSection.WORDS) {
                Text(
                    "${state.cards.count { it.repetitions >= 3 }}/${state.cards.size}",
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                    color = Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun SectionPill(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(999.dp), color = if (active) Yellow else Color.White) {
        Text(label, Modifier.padding(horizontal = 17.dp, vertical = 8.dp), color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun StudyContent(
    state: ReviewUiState,
    viewModel: ReviewViewModel,
    onOpenSource: (String, Long) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.current == null) {
            EmptyStudy(state, viewModel, Modifier.weight(1f))
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                state.session.take(2).asReversed().forEach { card ->
                    key(card.cardId) {
                        val current = card.cardId == state.current.cardId
                        StudyCard(
                            card = card,
                            busy = state.busy || !current,
                            onRate = if (current) viewModel::rate else { _ -> },
                            completed = state.completed + if (current) 0 else 1,
                            total = state.sessionTotal,
                            onToggleFavorite = if (current) ({ viewModel.toggleFavorite(card) }) else ({}),
                            onOpenSource = if (current) onOpenSource else { _, _ -> },
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                // Pre-draw the next full card behind the current one. Its layout,
                                // text and scroll containers are warm when it becomes current.
                                alpha = if (current) 1f else .001f
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewScopeDialog(state: ReviewUiState, onDismiss: () -> Unit, onSelect: (ReviewScope) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择复习范围", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                item {
                    ScopeOption("全部单词", "从符合当前难度的全部单词中随机抽取", state.reviewScope.sourceVideoId == null && !state.reviewScope.favoritesOnly) {
                        onSelect(ReviewScope())
                    }
                }
                item {
                    ScopeOption("我的收藏", "${state.allCards.count { it.isFavorite }} 张卡片", state.reviewScope.favoritesOnly) {
                        onSelect(ReviewScope(label = "我的收藏", favoritesOnly = true))
                    }
                }
                items(state.wordBooks, key = { it.sourceVideoId }) { book ->
                    ScopeOption(book.title, "${book.cardCount} 张卡片", state.reviewScope.sourceVideoId == book.sourceVideoId) {
                        onSelect(ReviewScope(book.sourceVideoId, book.title))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ScopeOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) Color(0xFFFFF3BF) else Cream, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            if (selected) Text("✓", color = Green, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SegmentedProgress(completed: Int, total: Int) {
    val progress = if (total == 0) 0f else completed.toFloat() / total
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(13.dp).clip(RoundedCornerShape(999.dp)), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(Green, Blue, Yellow, Orange, Purple, Red).forEachIndexed { index, color ->
            val filled = progress * 6f > index
            Box(Modifier.weight(1f).fillMaxHeight().background(if (filled) color else Border))
        }
    }
    Text("$completed / $total 卡片", Modifier.padding(top = 8.dp), color = Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
}

@Composable
private fun EmptyStudy(state: ReviewUiState, viewModel: ReviewViewModel, modifier: Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(32.dp), color = Color.White, shadowElevation = 4.dp, modifier = Modifier.padding(24.dp)) {
            Column(Modifier.padding(horizontal = 32.dp, vertical = 38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (state.cards.isEmpty()) "还没有生词卡" else "今天复习完成啦", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text(
                    if (state.cards.isEmpty()) "完成视频生词提取，或在生词本手动添加。" else "到期卡片已经全部完成，可以稍后再来。",
                    Modifier.padding(top = 10.dp), color = Muted, textAlign = TextAlign.Center,
                )
                Surface(onClick = viewModel::refreshSession, color = Green, shape = RoundedCornerShape(999.dp), modifier = Modifier.padding(top = 22.dp)) {
                    Text("刷新卡片", Modifier.padding(horizontal = 28.dp, vertical = 13.dp), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StudyCard(
    card: ReviewStudyCardRow,
    busy: Boolean,
    onRate: (ReviewRating) -> Unit,
    completed: Int,
    total: Int,
    onToggleFavorite: () -> Unit,
    onOpenSource: (String, Long) -> Unit,
    modifier: Modifier,
) {
    var flipped by rememberSaveable(card.cardId) { mutableStateOf(false) }
    var actionInFlight by remember(card.cardId) { mutableStateOf(false) }
    var dragOffset by remember(card.cardId) { mutableFloatStateOf(0f) }
    val entranceAlpha = remember(card.cardId) { Animatable(0f) }
    val entranceScale = remember(card.cardId) { Animatable(.97f) }
    val scope = rememberCoroutineScope()

    fun fling(rating: ReviewRating) {
        if (busy || actionInFlight) return
        actionInFlight = true
        scope.launch {
            Animatable(dragOffset).animateTo(
                if (rating == ReviewRating.GOOD) 1_100f else -1_100f,
                spring(stiffness = Spring.StiffnessMedium),
            ) { dragOffset = value }
            onRate(rating)
        }
    }

    LaunchedEffect(card.cardId) {
        dragOffset = 0f
        flipped = false
        actionInFlight = false
        entranceAlpha.snapTo(0f)
        entranceScale.snapTo(.97f)
        launch { entranceAlpha.animateTo(1f, tween(220)) }
        launch { entranceScale.animateTo(1f, tween(260)) }
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Keep the animated layer larger than the actual Surface. Surface draws its elevation
        // outside its own bounds; putting graphicsLayer directly on the card makes that shadow
        // vulnerable to off-screen layer clipping whenever a click/flip causes recomposition.
        // This stage owns the transform and permanently reserves room for every shadow edge.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = dragOffset
                    rotationZ = dragOffset / 45f
                    alpha = entranceAlpha.value * (1f - kotlin.math.abs(dragOffset) / 850f).coerceIn(.2f, 1f)
                    scaleX = entranceScale.value
                    scaleY = entranceScale.value
                    clip = false
                }
                .pointerInput(card.cardId, busy, actionInFlight) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                dragOffset > 120f -> fling(ReviewRating.GOOD)
                                dragOffset < -120f -> fling(ReviewRating.AGAIN)
                                else -> scope.launch {
                                    Animatable(dragOffset).animateTo(0f, spring()) { dragOffset = value }
                                }
                            }
                        },
                        onDragCancel = { scope.launch { Animatable(dragOffset).animateTo(0f, spring()) { dragOffset = value } } },
                    ) { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                    }
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 18.dp,
                        top = StudyCardTopGap,
                        end = 18.dp,
                        bottom = StudyCardBottomGap,
                    ),
            ) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(34.dp),
                    shadowElevation = 9.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, Border, RoundedCornerShape(34.dp)),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        CompactCardProgress(completed, total, card.isFavorite, onToggleFavorite)
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            AnimatedContent(flipped, label = "review-card-face") { showingBack ->
                                if (showingBack) CardBack(card, onOpenSource)
                                else CardFront(card)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RoundAction("×", Red, 48.dp) { fling(ReviewRating.AGAIN) }
                            Surface(onClick = { flipped = !flipped }, color = Purple.copy(alpha = .14f), shape = RoundedCornerShape(999.dp), modifier = Modifier.height(48.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(if (flipped) "查看正面" else "点击翻面", Modifier.padding(horizontal = 18.dp), color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            RoundAction("✓", Green, 48.dp) { fling(ReviewRating.GOOD) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactCardProgress(completed: Int, total: Int, favorite: Boolean, onToggleFavorite: () -> Unit) {
    val progress = if (total == 0) 0f else completed.toFloat() / total
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("$completed / $total", color = Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Row(Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(999.dp)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(Green, Blue, Yellow, Orange, Purple, Red).forEachIndexed { index, color ->
                Box(Modifier.weight(1f).fillMaxHeight().background(if (progress * 6f > index) color else Border))
            }
        }
        Surface(
            onClick = onToggleFavorite,
            color = Color(0xFFFFE58A).copy(alpha = .48f),
            shape = CircleShape,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (favorite) "★" else "☆", color = Color(0xFFE0A400), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CardFront(card: ReviewStudyCardRow) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(8.dp))
            Text(card.lemma, color = Ink, fontSize = frontLemmaFontSize(card.lemma.length), lineHeight = 44.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(formatPhonetic(card.phonetic) ?: "发音待补全", Modifier.padding(top = 13.dp), color = Muted, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Box(Modifier.padding(vertical = 22.dp).width(80.dp).height(1.dp).background(Border))
            Text(card.contextEn ?: "No example sentence yet.", color = Ink, fontSize = 20.sp, lineHeight = 29.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CardBack(card: ReviewStudyCardRow, onOpenSource: (String, Long) -> Unit) {
    val speaker = rememberPronunciationSpeaker()
    val contextualMeaningZh = ContextualChineseMeaningResolver.resolve(
        contextZh = card.contextZh,
        alignedMeaningZh = card.contextualMeaningZh,
        definitionZh = card.definitionZh,
        sourceTerms = listOfNotNull(card.sourceSurfaceForm, card.lemma),
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 27.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            card.lemma,
            color = Ink,
            fontSize = backLemmaFontSize(card.lemma.length),
            lineHeight = 38.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFFFFF1C7), shape = RoundedCornerShape(999.dp)) {
                Text(formatPhonetic(card.phonetic) ?: "音标待补全", Modifier.padding(horizontal = 13.dp, vertical = 6.dp), color = Color(0xFF8A5B00), fontWeight = FontWeight.Bold)
            }
            Surface(
                onClick = { speaker.speak(card.lemma, card.audioUrl) },
                shape = CircleShape,
                color = Color(0xFFE7F9F1),
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Text("♪", color = Green, fontWeight = FontWeight.Black, fontSize = 16.sp) }
            }
        }
        Text(
            formatDefinitionForReading(card.definitionZh ?: card.definitionEn ?: "释义待补全"),
            Modifier.fillMaxWidth().padding(top = 20.dp),
            color = Ink,
            fontSize = 20.sp,
            lineHeight = 29.sp,
            textAlign = TextAlign.Start,
            fontWeight = FontWeight.Bold,
        )
        contextualMeaningZh
            ?.takeIf { contextual -> contextual.isNotBlank() && !card.definitionZh.orEmpty().contains(contextual) }
            ?.let { contextual ->
                Surface(color = Color(0xFFFFF4D7), shape = RoundedCornerShape(999.dp), modifier = Modifier.padding(top = 13.dp)) {
                    Text("本句：$contextual", Modifier.padding(horizontal = 15.dp, vertical = 7.dp), color = Color(0xFF8A5B00), fontWeight = FontWeight.Bold)
                }
            }
        if (!card.contextEn.isNullOrBlank() || !card.contextZh.isNullOrBlank()) {
            Surface(color = Color(0xFFF7F7FC), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
                Column(Modifier.padding(horizontal = 17.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    card.contextEn?.takeIf(String::isNotBlank)?.let { english ->
                        Text(
                            highlightedExample(english, card.sourceSurfaceForm ?: card.lemma, Ink),
                            fontSize = 16.sp,
                            lineHeight = 23.sp,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    card.contextZh?.takeIf(String::isNotBlank)?.let { chinese ->
                        Text(
                            highlightedExample(chinese, contextualMeaningZh, Muted),
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        if (card.sourceVideoId != null && card.sourceStartMs != null) {
            Surface(onClick = { onOpenSource(card.sourceVideoId, card.sourceStartMs) }, color = Color(0xFFEAF2FF), shape = RoundedCornerShape(999.dp), modifier = Modifier.padding(top = 23.dp)) {
                Text("▶  回到视频原句", Modifier.padding(horizontal = 22.dp, vertical = 11.dp), color = Blue, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun frontLemmaFontSize(length: Int) = when {
    length >= 18 -> 24.sp
    length >= 14 -> 27.sp
    length >= 10 -> 31.sp
    else -> 38.sp
}

private fun backLemmaFontSize(length: Int) = when {
    length >= 18 -> 24.sp
    length >= 14 -> 27.sp
    length >= 10 -> 30.sp
    else -> 34.sp
}

internal fun highlightedExample(text: String, target: String?, baseColor: Color) = buildAnnotatedString {
    if (target.isNullOrBlank()) {
        withStyle(SpanStyle(color = baseColor)) { append(text) }
        return@buildAnnotatedString
    }
    val escaped = target.trim().split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }
    val matches = Regex("(?i)(?<![A-Za-z0-9])$escaped(?![A-Za-z0-9])").findAll(text).toList()
    if (matches.isEmpty()) {
        withStyle(SpanStyle(color = baseColor)) { append(text) }
        return@buildAnnotatedString
    }
    var cursor = 0
    matches.forEach { match ->
        if (match.range.first > cursor) withStyle(SpanStyle(color = baseColor)) { append(text.substring(cursor, match.range.first)) }
        withStyle(
            SpanStyle(
                color = Ink,
                background = Color(0xFFFFDD74),
                fontWeight = FontWeight.Black,
            ),
        ) { append(match.value) }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) withStyle(SpanStyle(color = baseColor)) { append(text.substring(cursor)) }
}

internal fun formatDefinitionForReading(definition: String): String = definition
    .split(Regex("[;；]+"))
    .map(String::trim)
    .filter(String::isNotBlank)
    .joinToString("\n")

@Composable
private fun RoundAction(label: String, border: Color, size: androidx.compose.ui.unit.Dp, enabled: Boolean = true, contentColor: Color = border, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) border.copy(alpha = .16f) else Border.copy(alpha = .28f),
        shadowElevation = 0.dp,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (enabled) contentColor else Muted.copy(alpha = .35f),
                fontSize = if (size >= 48.dp) 27.sp else 23.sp,
                fontWeight = FontWeight.Light,
            )
        }
    }
}

private enum class WordFilter { ALL, FAVORITES, DUE, NEW, MASTERED }

@Composable
private fun WordBookContent(state: ReviewUiState, viewModel: ReviewViewModel, modifier: Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(WordFilter.ALL) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ReviewStudyCardRow?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var selectedForDeletion by rememberSaveable { mutableStateOf(setOf<String>()) }
    var confirmDeletion by remember { mutableStateOf(false) }
    var showAllDifficulties by rememberSaveable { mutableStateOf(false) }
    val expandedSources = remember { mutableStateMapOf<String, Boolean>() }
    val now = System.currentTimeMillis()
    val sourceCards = if (showAllDifficulties) state.allCards else state.cards
    val cards = sourceCards.filter { card ->
        (query.isBlank() || card.lemma.contains(query, true) || card.definitionZh.orEmpty().contains(query, true)) && when (filter) {
            WordFilter.ALL -> true
            WordFilter.FAVORITES -> card.isFavorite
            WordFilter.DUE -> card.dueAt <= now
            WordFilter.NEW -> card.repetitions == 0
            WordFilter.MASTERED -> card.repetitions >= 3
        }
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = Color.White, shape = RoundedCornerShape(999.dp), modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Ink, fontSize = 16.sp),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                    decorationBox = { field -> if (query.isEmpty()) Text("搜索单词或释义", color = Muted); field() },
                )
            }
            SmallAction(if (grid) "☷" else "▦") { grid = !grid }
            SmallAction("＋") { adding = true }
            Surface(
                onClick = {
                    if (deleting && selectedForDeletion.isNotEmpty()) confirmDeletion = true
                    else {
                        deleting = !deleting
                        selectedForDeletion = emptySet()
                    }
                },
                color = if (deleting) Red else Color.White,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    if (deleting && selectedForDeletion.isNotEmpty()) "删除(${selectedForDeletion.size})" else if (deleting) "取消" else "删除",
                    Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                    color = if (deleting) Color.White else Red,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            WordFilter.entries.forEach { value ->
                val label = when (value) { WordFilter.ALL -> "全部"; WordFilter.FAVORITES -> "我的收藏"; WordFilter.DUE -> "到期"; WordFilter.NEW -> "未学"; WordFilter.MASTERED -> "已掌握" }
                SectionPill(label, filter == value) { filter = value }
            }
            SectionPill(if (showAllDifficulties) "当前难度" else "显示全部难度", showAllDifficulties) {
                showAllDifficulties = !showAllDifficulties
            }
        }
        if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("没有符合条件的卡片", color = Muted) }
        } else if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(155.dp),
                contentPadding = PaddingValues(8.dp, 10.dp, 8.dp, 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val grouped = cards.groupBy { it.sourceVideoTitle ?: "手动添加 / 其他" }
                grouped.forEach { (source, sourceCards) ->
                    val expanded = expandedSources[source] ?: true
                    item(key = "header-$source", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        WordSourceHeader(source, sourceCards, expanded) { expandedSources[source] = !expanded }
                    }
                    if (expanded) items(sourceCards, key = { it.cardId }) { card ->
                        WordGridCard(card, selected = card.lexemeId in selectedForDeletion) {
                            if (deleting) selectedForDeletion = selectedForDeletion.toggle(card.lexemeId) else editing = card
                        }
                    }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(8.dp, 10.dp, 8.dp, 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val grouped = cards.groupBy { it.sourceVideoTitle ?: "手动添加 / 其他" }
                grouped.forEach { (source, sourceCards) ->
                    val expanded = expandedSources[source] ?: true
                    item("header-$source") { WordSourceHeader(source, sourceCards, expanded) { expandedSources[source] = !expanded } }
                    if (expanded) items(sourceCards, key = { it.cardId }) { card ->
                        WordListCard(card, selected = card.lexemeId in selectedForDeletion) {
                            if (deleting) selectedForDeletion = selectedForDeletion.toggle(card.lexemeId) else editing = card
                        }
                    }
                }
            }
        }
    }
    if (adding) WordEditorDialog(null, onDismiss = { adding = false }) { word, phonetic, pos, zh -> viewModel.addCard(word, phonetic, pos, zh); adding = false }
    editing?.let { card ->
        WordEditorDialog(card, onDismiss = { editing = null }, onDelete = { viewModel.deleteCard(card); editing = null }) { word, phonetic, pos, zh ->
            viewModel.editCard(card, word, phonetic, pos, zh); editing = null
        }
    }
    if (confirmDeletion) {
        val selectedCards = state.cards.filter { it.lexemeId in selectedForDeletion }
        AlertDialog(
            onDismissRequest = { confirmDeletion = false },
            containerColor = Cream,
            title = { Text("删除生词", fontWeight = FontWeight.Black) },
            text = { Text("确定删除选中的 ${selectedCards.size} 个生词吗？对应复习记录也会一并删除。", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCards(selectedCards)
                    selectedForDeletion = emptySet()
                    deleting = false
                    confirmDeletion = false
                }) { Text("删除", color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmDeletion = false }) { Text("取消", color = Muted) } },
        )
    }
}

@Composable
private fun WordSourceHeader(source: String, cards: List<ReviewStudyCardRow>, expanded: Boolean, onToggle: () -> Unit) {
    Surface(onClick = onToggle, color = Color.Transparent, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (expanded) "⌄" else "›", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(source, Modifier.weight(1f).padding(start = 8.dp), color = Muted, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${cards.count { it.repetitions >= 3 }}/${cards.size}", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun WordListCard(card: ReviewStudyCardRow, selected: Boolean = false, onEdit: () -> Unit) {
    Surface(
        onClick = onEdit,
        color = if (selected) Color(0xFFFFE1E1) else Color.White,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 2.dp,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, Red) else null,
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(card.lemma, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    card.phonetic?.let { Text(it, color = Muted, fontSize = 13.sp) }
                }
                Text(card.definitionZh ?: card.definitionEn ?: "释义待补全", Modifier.padding(top = 7.dp), color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                card.contextualMeaningZh
                    ?.takeIf { it.isNotBlank() && !card.definitionZh.orEmpty().contains(it) }
                    ?.let { Text("本句：$it", Modifier.padding(top = 4.dp), color = Color(0xFF8A5B00), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (selected) Text("✓", color = Red, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(card.partOfSpeech ?: "词汇", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(dueLabel(card), Modifier.padding(top = 8.dp), color = if (card.dueAt <= System.currentTimeMillis()) Red else Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun WordGridCard(card: ReviewStudyCardRow, selected: Boolean = false, onEdit: () -> Unit) {
    Surface(
        onClick = onEdit,
        color = if (selected) Color(0xFFFFE1E1) else Color.White,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.aspectRatio(.92f),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, Red) else null,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(card.lemma, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                card.phonetic?.let { Text(it, Modifier.padding(top = 5.dp), color = Muted, fontSize = 12.sp) }
                Text(card.definitionZh ?: card.definitionEn ?: "释义待补全", Modifier.padding(top = 12.dp), color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                card.contextualMeaningZh
                    ?.takeIf { it.isNotBlank() && !card.definitionZh.orEmpty().contains(it) }
                    ?.let { Text("本句：$it", Modifier.padding(top = 5.dp), color = Color(0xFF8A5B00), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Text(dueLabel(card), color = if (card.dueAt <= System.currentTimeMillis()) Red else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (selected) Text("已选择", color = Red, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

private fun dueLabel(card: ReviewStudyCardRow): String = when {
    card.repetitions == 0 -> "未学习"
    card.dueAt <= System.currentTimeMillis() -> "今天到期"
    else -> "${Instant.ofEpochMilli(card.dueAt).atZone(ZoneId.systemDefault()).toLocalDate()} 到期"
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White, modifier = Modifier.size(45.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(label, color = Ink, fontWeight = FontWeight.Black, fontSize = 20.sp) }
    }
}

@Composable
private fun WordEditorDialog(
    card: ReviewStudyCardRow?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (String, String?, String?, String?) -> Unit,
) {
    var word by remember(card?.cardId) { mutableStateOf(card?.lemma.orEmpty()) }
    var phonetic by remember(card?.cardId) { mutableStateOf(card?.phonetic.orEmpty()) }
    var pos by remember(card?.cardId) { mutableStateOf(card?.partOfSpeech.orEmpty()) }
    var definition by remember(card?.cardId) { mutableStateOf(card?.definitionZh.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Cream,
        title = { Text(if (card == null) "添加生词" else "编辑生词", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EditorField(word, { word = it }, "英文单词或短语")
                EditorField(phonetic, { phonetic = it }, "音标（可选）")
                EditorField(pos, { pos = it }, "词性（可选）")
                EditorField(definition, { definition = it }, "中文释义（可选）")
            }
        },
        confirmButton = { TextButton(onClick = { if (word.isNotBlank()) onSave(word, phonetic, pos, definition) }) { Text("保存", color = Green, fontWeight = FontWeight.Bold) } },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("删除", color = Red, fontWeight = FontWeight.Bold) } }
                TextButton(onClick = onDismiss) { Text("取消", color = Muted) }
            }
        },
    )
}

@Composable
private fun EditorField(value: String, onValueChange: (String) -> Unit, hint: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
        BasicTextField(value, onValueChange, textStyle = TextStyle(color = Ink, fontSize = 16.sp), modifier = Modifier.fillMaxWidth().padding(14.dp), decorationBox = { field -> if (value.isBlank()) Text(hint, color = Muted); field() })
    }
}

@Composable
private fun StatsContent(state: ReviewUiState, viewModel: ReviewViewModel, modifier: Modifier) {
    val overview = state.overview
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp, 10.dp, 8.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricCard("今日已学", overview.todayLearned.toString(), Green, Modifier.weight(1f))
                MetricCard("待复习", overview.dueCount.toString(), Orange, Modifier.weight(1f))
                MetricCard("已掌握", overview.masteredCount.toString(), Blue, Modifier.weight(1f))
            }
        }
        if (state.allDueCount > overview.dueCount) {
            item {
                Text(
                    "当前显示 ${state.difficulty.label}及以上；全部难度共有 ${state.allDueCount} 张到期卡片。",
                    Modifier.padding(horizontal = 6.dp), color = Muted, fontSize = 12.sp,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricCard("连续学习", "${overview.currentStreak} 天", Purple, Modifier.weight(1f))
                MetricCard("累计学习", "${overview.totalLearningDays} 天", Yellow, Modifier.weight(1f))
            }
        }
        item { HeatmapCard(overview.daily, viewModel::selectDate) }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(25.dp), shadowElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("间隔复习", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    Text("卡片采用基于 SM-2 的二元调度：记住会逐步延长间隔，没记住会在 1 天后再次出现。", Modifier.padding(top = 8.dp), color = Muted, lineHeight = 22.sp)
                }
            }
        }
    }
    state.selectedDayStats?.let { DayDetailDialog(it, viewModel::clearSelectedDate) }
}

@Composable
private fun MetricCard(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(color = Color.White, shape = RoundedCornerShape(22.dp), shadowElevation = 2.dp, modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 17.dp)) {
            Box(Modifier.size(9.dp).background(color, CircleShape))
            Text(value, Modifier.padding(top = 10.dp), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(label, Modifier.padding(top = 2.dp), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HeatmapCard(daily: Map<LocalDate, DailyReviewStats>, onDate: (LocalDate) -> Unit) {
    val today = remember { LocalDate.now() }
    val window = remember(today) { ReviewStatsAggregator.windowDates(today) }
    val weeks = remember(today) { ReviewStatsAggregator.heatmapWeeks(today) }
    val scroll = rememberScrollState(Int.MAX_VALUE)
    Surface(color = Color.White, shape = RoundedCornerShape(25.dp), shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("近 12 个月学习热力图", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Text("点击日期查看复习详情", Modifier.padding(top = 3.dp), color = Muted, fontSize = 12.sp)
            Row(Modifier.padding(top = 16.dp)) {
                Column(Modifier.padding(top = 21.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("一", "", "三", "", "五", "", "日").forEach { Text(it, Modifier.height(13.dp).width(17.dp), color = Muted, fontSize = 8.sp) }
                }
                Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    weeks.forEachIndexed { weekIndex, week ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            val monthLabel = week.firstOrNull { it.dayOfMonth == 1 }?.format(DateTimeFormatter.ofPattern("M月"))
                                ?: if (weekIndex == 0) window.first().format(DateTimeFormatter.ofPattern("M月")) else ""
                            Text(monthLabel, Modifier.height(16.dp), color = Muted, fontSize = 8.sp, maxLines = 1)
                            week.forEach { date ->
                                val visible = date in window
                                val stats = daily[date]
                                val color = if (visible) heatColor(stats?.level ?: 0) else Color.Transparent
                                Box(
                                    Modifier.size(13.dp).clip(RoundedCornerShape(3.dp)).background(color)
                                        .then(if (visible) Modifier.clickable { onDate(date) } else Modifier)
                                        .semantics { contentDescription = if (visible) "$date，复习 ${stats?.reviews ?: 0} 次" else "" },
                                )
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("少", color = Muted, fontSize = 10.sp)
                (0..4).forEach { Box(Modifier.padding(start = 5.dp).size(12.dp).background(heatColor(it), RoundedCornerShape(3.dp))) }
                Text("多", Modifier.padding(start = 5.dp), color = Muted, fontSize = 10.sp)
            }
        }
    }
}

private fun heatColor(level: Int): Color = when (level) {
    0 -> Color(0xFFEDEDFC)
    1 -> Color(0xFFC7F3E1)
    2 -> Color(0xFF7DE2BC)
    3 -> Color(0xFF2FD397)
    else -> Green
}

@Composable
private fun DayDetailDialog(stats: DailyReviewStats, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Cream,
        title = { Text(stats.date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")), fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                DetailRow("复习次数", stats.reviews)
                DetailRow("记住", stats.good)
                DetailRow("没记住", stats.again)
                DetailRow("新学单词", stats.newWords)
                DetailRow("正确率", "${stats.accuracy}%")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成", color = Green, fontWeight = FontWeight.Bold) } },
    )
}

@Composable private fun DetailRow(label: String, value: Any) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Muted); Text(value.toString(), color = Ink, fontWeight = FontWeight.Black) }
}

@Composable
private fun rememberPronunciationSpeaker(): PronunciationSpeaker {
    val context = LocalContext.current
    val speaker = remember { PronunciationSpeaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.close() } }
    return speaker
}

private class PronunciationSpeaker(private val context: android.content.Context) : AutoCloseable {
    private var ttsReady = false
    private var pendingWord: String? = null
    private lateinit var tts: TextToSpeech
    private var player: MediaPlayer? = null

    init { initializeTts() }

    private fun initializeTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) pendingWord?.let { queued ->
                pendingWord = null
                speakWithTts(queued)
            }
        }
    }

    fun speak(word: String, audioUrl: String?) {
        player?.release()
        player = null
        if (audioUrl.isNullOrBlank()) return speakWithTts(word)
        runCatching {
            MediaPlayer().also { media ->
                player = media
                media.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(AudioAttributes.USAGE_MEDIA).build())
                media.setDataSource(audioUrl)
                media.setOnPreparedListener { it.start() }
                media.setOnCompletionListener { it.release(); if (player === it) player = null }
                media.setOnErrorListener { failed, _, _ -> failed.release(); if (player === failed) player = null; speakWithTts(word); true }
                media.prepareAsync()
            }
        }.onFailure { speakWithTts(word) }
    }

    private fun speakWithTts(word: String) {
        if (!ttsReady) {
            pendingWord = word
            runCatching { tts.shutdown() }
            initializeTts()
            return
        }
        val localeResult = tts.setLanguage(Locale.US)
        if (localeResult == TextToSpeech.LANG_MISSING_DATA || localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH)
        }
        val result = tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, "review-${word.hashCode()}")
        if (result == TextToSpeech.ERROR) {
            pendingWord = word
            tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, "review-fallback-${System.currentTimeMillis()}")
        }
    }

    override fun close() { player?.release(); player = null; tts.stop(); tts.shutdown() }
}
