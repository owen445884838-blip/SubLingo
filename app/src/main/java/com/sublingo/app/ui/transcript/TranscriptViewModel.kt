package com.sublingo.app.ui.transcript

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sublingo.app.data.db.SubtitleCueDao
import com.sublingo.app.data.db.SubtitleTrackDao
import com.sublingo.app.data.db.SubtitleWordAlignmentDao
import com.sublingo.app.data.db.SubtitleWordAlignmentEntity
import com.sublingo.app.data.db.VocabularyDao
import com.sublingo.app.data.db.ProcessingJobDao
import com.sublingo.app.data.db.VideoDao
import com.sublingo.app.domain.repository.VideoRepository
import com.sublingo.app.data.media.SubtitlePipelineScheduler
import com.sublingo.app.data.media.RollingSubtitleNormalizer
import com.sublingo.app.data.vocabulary.VocabularyPipelineContract
import com.sublingo.app.data.vocabulary.VocabularyDifficulty
import com.sublingo.app.data.vocabulary.VocabularyDifficultyPreferences
import com.sublingo.app.data.vocabulary.ContextualChineseMeaningResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class TranscriptRow(
    val sequence: Int,
    val startMs: Long,
    val endMs: Long,
    val english: String?,
    val chinese: String?,
    val highlights: List<TranscriptHighlight> = emptyList(),
)
data class TranscriptHighlight(
    val id: String,
    val surfaceForm: String,
    val chineseCandidates: List<String>,
    val itemType: com.sublingo.app.data.vocabulary.VocabularyItemType = com.sublingo.app.data.vocabulary.VocabularyItemType.WORD,
    val englishOccurrence: Int = 0,
)
data class TranscriptVocabulary(
    val id: String,
    val lemma: String,
    val normalizedLemma: String,
    val firstSequence: Int,
)
data class TranscriptUiState(
    val videoId: String = "",
    val title: String = "逐字稿",
    val filePath: String? = null,
    val companionAudioPath: String? = null,
    val durationMs: Long = 0L,
    val lastPlayedPositionMs: Long = 0L,
    val rows: List<TranscriptRow> = emptyList(),
    val keyVocabulary: List<TranscriptVocabulary> = emptyList(),
    val isLoaded: Boolean = false,
)

/**
 * Keeps the already-rendered immersive-player data alive for the short navigation handoff.
 * Room remains the source of truth; this snapshot only prevents an empty destination frame while
 * the transcript's highlighted rows are assembled.
 */
internal data class TranscriptTransitionSnapshot(
    val videoId: String,
    val title: String,
    val filePath: String?,
    val companionAudioPath: String?,
    val durationMs: Long,
    val positionMs: Long,
    val englishCues: List<com.sublingo.app.data.db.SubtitleCueEntity>,
    val chineseCues: List<com.sublingo.app.data.db.SubtitleCueEntity>,
) {
    fun asUiState(): TranscriptUiState = TranscriptUiState(
        videoId = videoId,
        title = title,
        filePath = filePath,
        companionAudioPath = companionAudioPath,
        durationMs = durationMs,
        lastPlayedPositionMs = positionMs,
        rows = TranscriptAssembler.assemble(englishCues, chineseCues),
        isLoaded = true,
    )
}

internal object TranscriptTransitionHandoff {
    private var snapshot: TranscriptTransitionSnapshot? = null

    @Synchronized
    fun publish(value: TranscriptTransitionSnapshot) {
        snapshot = value
    }

    @Synchronized
    fun peek(videoId: String): TranscriptTransitionSnapshot? = snapshot?.takeIf { it.videoId == videoId }

    @Synchronized
    fun clear(value: TranscriptTransitionSnapshot) {
        if (snapshot === value) snapshot = null
    }
}

object TranscriptAssembler {
    fun assemble(
        english: List<com.sublingo.app.data.db.SubtitleCueEntity>,
        chinese: List<com.sublingo.app.data.db.SubtitleCueEntity>,
    ): List<TranscriptRow> {
        // New captions come from the configured ASR provider and are already sentence-timed.
        // Keep persisted platform-track cleanup only as a compatibility fallback for old videos.
        val normalizedEnglish = if (english.firstOrNull()?.trackId?.endsWith("-en-asr") == true) {
            english.map { com.sublingo.app.data.media.NormalizedSubtitleCue(it, it.text) }
        } else RollingSubtitleNormalizer.normalizeEntities(english)
        val normalizedChinese = if (english.firstOrNull()?.trackId?.endsWith("-en-asr") == true) {
            chinese.map { com.sublingo.app.data.media.NormalizedSubtitleCue(it, it.text) }
        } else RollingSubtitleNormalizer.normalizeEntities(chinese)
        val enBySequence = normalizedEnglish.associateBy { it.source.sequence }
        val zhBySequence = normalizedChinese.associateBy { it.source.sequence }
        val sequences = if (enBySequence.isNotEmpty()) enBySequence.keys else zhBySequence.keys
        return sequences.sorted().map { sequence ->
            val enCue = enBySequence[sequence]
            val zhCue = zhBySequence[sequence]
            val chineseText = zhCue?.let { normalized ->
                if (enCue != null && enCue.removedPrefixFraction > normalized.removedPrefixFraction + .15f) {
                    RollingSubtitleNormalizer.removeApproximatePrefix(normalized.text, enCue.removedPrefixFraction)
                } else {
                    normalized.text
                }
            }
            TranscriptRow(
                sequence = sequence,
                startMs = enCue?.source?.startMs ?: zhCue?.source?.startMs ?: 0L,
                endMs = enCue?.source?.endMs ?: zhCue?.source?.endMs ?: 0L,
                english = enCue?.text,
                chinese = chineseText,
            )
        }
    }
}

/**
 * Translation versions before the local-gap repair used the complete English sentence as the
 * anchor for an uncovered Chinese particle. A single such row wins the aligner's longest-surface
 * selection and hides every real word pair in that cue. Re-anchor those legacy rows to the nearest
 * persisted local pair so existing transcripts become usable without another LLM request.
 */
internal fun localizeSentenceFallbackAlignments(
    sourceText: String,
    alignments: List<SubtitleWordAlignmentEntity>,
): List<SubtitleWordAlignmentEntity> {
    val normalizedSource = sourceText.trim()
    val localPairs = alignments.filter { it.englishSurface.trim() != normalizedSource }
    if (localPairs.isEmpty()) return alignments
    return alignments.map { alignment ->
        if (alignment.englishSurface.trim() != normalizedSource) alignment
        else {
            val anchor = localPairs.minWithOrNull(
                compareBy<SubtitleWordAlignmentEntity> { kotlin.math.abs(it.ordinal - alignment.ordinal) }
                    .thenBy { if (it.ordinal >= alignment.ordinal) 0 else 1 },
            )
            anchor?.let {
                alignment.copy(
                    englishSurface = it.englishSurface,
                    englishOccurrence = it.englishOccurrence,
                )
            } ?: alignment
        }
    }
}

/**
 * A single English occurrence should not light up unrelated Chinese regions. Translation repair can
 * legitimately split one meaning across adjacent rows (for example `did not` -> `没` + `有`), but
 * legacy fallback rows sometimes attach distant Chinese gaps to the same English anchor. Keep the
 * most informative contiguous cluster and discard the disjoint fallback fragments at display time.
 */
internal fun retainBestContiguousChineseCluster(
    chineseText: String,
    alignments: List<SubtitleWordAlignmentEntity>,
): List<SubtitleWordAlignmentEntity> {
    if (chineseText.isBlank() || alignments.size < 2) return alignments
    return alignments
        .groupBy { it.englishSurface.lowercase().trim().replace(Regex("\\s+"), " ") to it.englishOccurrence }
        .values
        .flatMap { group ->
            if (group.size < 2) return@flatMap group
            data class Positioned(val alignment: SubtitleWordAlignmentEntity, val start: Int, val endExclusive: Int)
            var cursor = 0
            val positioned = group.sortedBy { it.ordinal }.mapNotNull { alignment ->
                val surface = alignment.chineseSurface.trim()
                val start = chineseText.indexOf(surface, startIndex = cursor).takeIf { it >= 0 }
                    ?: chineseText.indexOf(surface).takeIf { it >= 0 }
                    ?: return@mapNotNull null
                cursor = start + surface.length
                Positioned(alignment, start, cursor)
            }
            if (positioned.size < 2) return@flatMap group
            val clusters = mutableListOf<MutableList<Positioned>>()
            positioned.forEach { item ->
                val previous = clusters.lastOrNull()?.lastOrNull()
                val gapIsOnlyPunctuation = previous != null && chineseText
                    .substring(previous.endExclusive.coerceAtMost(item.start), item.start)
                    .none(Char::isLetterOrDigit)
                if (previous != null && (item.start <= previous.endExclusive || gapIsOnlyPunctuation)) {
                    clusters.last() += item
                } else {
                    clusters += mutableListOf(item)
                }
            }
            val bestSingle = positioned.maxWithOrNull(
                compareBy<Positioned> { it.alignment.chineseSurface.count(Char::isLetterOrDigit) }
                    .thenBy { -it.start },
            ) ?: return@flatMap group
            val compactCluster = clusters.firstOrNull { cluster ->
                bestSingle in cluster && cluster.size > 1 && cluster.all { item ->
                    item.alignment.chineseSurface.count(Char::isLetterOrDigit) <= 1
                }
            }
            (compactCluster ?: listOf(bestSingle)).map { it.alignment }
        }
        .sortedBy { it.ordinal }
}

@HiltViewModel
class TranscriptViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val videoRepository: VideoRepository,
    trackDao: SubtitleTrackDao,
    cueDao: SubtitleCueDao,
    wordAlignmentDao: SubtitleWordAlignmentDao,
    private val vocabularyDao: VocabularyDao,
    videoDao: VideoDao,
    processingJobDao: ProcessingJobDao,
    subtitlePipelineScheduler: SubtitlePipelineScheduler,
    difficultyPreferences: VocabularyDifficultyPreferences,
) : ViewModel() {
    private val videoId: String = savedStateHandle["videoId"] ?: ""
    init {
        if (videoId.isNotBlank()) viewModelScope.launch {
            val video = videoDao.getById(videoId)
            val currentJob = processingJobDao.getByVideoId(videoId)
            val vocabularyAlreadyRunning = currentJob?.state in setOf(
                com.sublingo.app.domain.model.ProcessingState.PENDING,
                com.sublingo.app.domain.model.ProcessingState.RUNNING,
            ) && currentJob?.currentStage == com.sublingo.app.domain.model.ProcessingStage.VOCABULARY
            // v10 data already contains the complete typed phrase set. CEFR metadata can be
            // backfilled locally, so opening an existing transcript must not trigger an LLM call.
            if (video != null && video.vocabularyVersion < VocabularyPipelineContract.MINIMUM_AUTO_REFRESH_VERSION && !vocabularyAlreadyRunning) {
                val jobId = currentJob?.id ?: "job-$videoId"
                subtitlePipelineScheduler.enqueueVocabularyRefresh(videoId, jobId)
            }
        }
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val subtitleRows = trackDao.observeByVideoId(videoId).flatMapLatest { tracks ->
        val english = tracks.firstOrNull { it.language.startsWith("en", true) && it.kind == "ASR" }
            ?: tracks.firstOrNull { it.language.startsWith("en", true) }
        val chinese = tracks.firstOrNull { it.language.startsWith("zh", true) && (it.sourceTrackId == english?.id || english == null) }
        combine(
            english?.let { cueDao.observeByTrackId(it.id) } ?: flowOf(emptyList()),
            chinese?.let { cueDao.observeByTrackId(it.id) } ?: flowOf(emptyList()),
        ) { en, zh ->
            TranscriptAssembler.assemble(en, zh)
        }
    }

    private val rows = combine(
        subtitleRows,
        vocabularyDao.observeTranscriptOccurrences(videoId),
        wordAlignmentDao.observeByVideoId(videoId),
    ) { transcriptRows, occurrences, translatedAlignments ->
        val bySequence = occurrences.groupBy { it.sequence }
        val translationPairsBySequence = translatedAlignments.groupBy { it.sequence }
        val knownTranslations = occurrences.groupBy { it.lexemeId }.mapValues { (_, rows) ->
            rows.mapNotNull { it.translationZh?.trim()?.takeIf(String::isNotEmpty) }.distinct()
        }
        transcriptRows.map { row ->
            val chineseText = row.chinese.orEmpty()
            val translatedPairs = retainBestContiguousChineseCluster(
                chineseText = chineseText,
                alignments = localizeSentenceFallbackAlignments(
                    sourceText = row.english.orEmpty(),
                    alignments = translationPairsBySequence[row.sequence].orEmpty()
                        .filterNot { it.source == com.sublingo.app.data.media.TranslationWordPair.SOURCE_GAP_REPAIR },
                ),
            )
                .groupBy { alignment ->
                    alignment.englishSurface.lowercase().trim().replace(Regex("\\s+"), " ") to alignment.englishOccurrence
                }
                .map { (key, alignments) ->
                    val (normalizedEnglish, occurrence) = key
                    TranscriptHighlight(
                        id = "translation-${row.sequence}-${normalizedEnglish.hashCode().toUInt().toString(16)}-$occurrence",
                        surfaceForm = alignments.first().englishSurface,
                        chineseCandidates = alignments.map { it.chineseSurface }.distinct(),
                        englishOccurrence = occurrence,
                    )
                }
            val pairedOccurrences = bySequence[row.sequence].orEmpty()
                .mapNotNull { occurrence ->
                    val contextualChinese = ContextualChineseMeaningResolver.resolve(
                        contextZh = chineseText,
                        alignedMeaningZh = occurrence.translationZh,
                        definitionZh = occurrence.definitionZh,
                        alignedCandidatesZh = knownTranslations[occurrence.lexemeId].orEmpty(),
                        sourceTerms = listOf(occurrence.surfaceForm, occurrence.lemma),
                    )
                    contextualChinese?.let { occurrence to it }
                }
                .groupBy { it.first.lexemeId }
                .mapNotNull { (_, matches) -> matches.maxByOrNull { it.second.length } }
            row.copy(
                highlights = (translatedPairs + pairedOccurrences.map { (occurrence, contextualChinese) ->
                    TranscriptHighlight(
                        id = occurrence.lexemeId,
                        surfaceForm = occurrence.surfaceForm,
                        itemType = runCatching { com.sublingo.app.data.vocabulary.VocabularyItemType.valueOf(occurrence.itemType) }
                            .getOrDefault(com.sublingo.app.data.vocabulary.VocabularyItemType.WORD),
                        // A transcript learning highlight is bilingual by contract. Occurrences
                        // without a validated exact Chinese span remain available in the word book
                        // but are not rendered as an unpaired English-only playback highlight.
                        chineseCandidates = listOf(contextualChinese),
                    )
                }).distinctBy { it.surfaceForm.lowercase() to it.chineseCandidates.firstOrNull() },
            )
        }
    }

    val uiState: StateFlow<TranscriptUiState> = combine(
        videoRepository.observeVideos(),
        rows,
        vocabularyDao.observeTranscriptVocabulary(videoId),
    ) { videos, transcriptRows, vocabulary ->
        val video = videos.firstOrNull { it.id == videoId }
        val videoFile = video?.filePath?.let(::File)
        val companionAudio = videoFile?.parentFile?.listFiles().orEmpty()
            .filter { file ->
                file.isFile && file.absolutePath != videoFile?.absolutePath &&
                    file.extension.lowercase() in setOf("m4a", "aac", "mp3", "opus", "ogg") &&
                    file.length() > 0L
            }
            .maxByOrNull(File::length)
        TranscriptUiState(
            videoId = videoId,
            title = video?.title ?: "双语逐字稿",
            filePath = video?.filePath,
            companionAudioPath = companionAudio?.absolutePath,
            durationMs = video?.durationMs ?: 0L,
            lastPlayedPositionMs = video?.lastPlayedPositionMs ?: 0L,
            rows = transcriptRows,
            keyVocabulary = vocabulary.map {
                TranscriptVocabulary(it.lexemeId, it.lemma, it.normalizedLemma, it.firstSequence)
            },
            isLoaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranscriptUiState(videoId))

    fun export(mode: TranscriptMode, markdown: Boolean): String = buildString {
        if (markdown) appendLine("# ${uiState.value.title}\n")
        uiState.value.rows.forEach { row ->
            if (markdown) appendLine("## ${formatTime(row.startMs)}") else appendLine("[${formatTime(row.startMs)}]")
            if (mode != TranscriptMode.CHINESE) row.english?.let(::appendLine)
            if (mode != TranscriptMode.ENGLISH) row.chinese?.let(::appendLine)
            appendLine()
        }
    }

    fun savePosition(positionMs: Long) {
        if (videoId.isBlank()) return
        viewModelScope.launch { videoRepository.updatePlaybackPosition(videoId, positionMs.coerceAtLeast(0L)) }
    }
}

enum class TranscriptMode { ENGLISH, CHINESE, BILINGUAL }
fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
