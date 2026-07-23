package com.sublingo.app.ui.transcript

data class DisplayToken(
    val text: String,
    val alignmentId: Int? = null,
    val alignmentIds: Set<Int> = alignmentId?.let(::setOf).orEmpty(),
    val isWord: Boolean = false,
)

data class PlaybackWindow(val alignmentId: Int, val startFraction: Float, val endFraction: Float)

data class AlignedTranscript(
    val english: List<DisplayToken>,
    val chinese: List<DisplayToken>,
    val playbackWindows: List<PlaybackWindow> = emptyList(),
    val highlightAlignmentIds: Map<String, Int> = emptyMap(),
)

internal data class TranscriptFollowTarget(val sequence: Int, val rowIndex: Int)

internal fun displayedTranscriptAlignmentId(
    aligned: AlignedTranscript,
    positionMs: Long,
    cueStartMs: Long,
    cueEndMs: Long,
): Int? {
    // A followed cue may be scrolled into view during the preceding silence. It must remain
    // visually idle until playback actually enters its timestamp window; otherwise the first
    // phrase flashes early and then appears to restart when speech reaches it.
    if (positionMs < cueStartMs) return null
    return TranscriptWordAligner.activeAlignmentId(aligned, positionMs, cueStartMs, cueEndMs)
}

internal fun nextTranscriptFollowTarget(
    rows: List<TranscriptRow>,
    positionMs: Long,
    currentSequence: Int?,
): TranscriptFollowTarget? {
    val currentIndex = rows.indexOfFirst { it.sequence == currentSequence }
    if (currentIndex < 0) return null
    val nextIndex = currentIndex + 1
    val targetIndex = if (nextIndex < rows.size && positionMs >= rows[currentIndex].endMs) nextIndex else currentIndex
    return TranscriptFollowTarget(rows[targetIndex].sequence, targetIndex)
}

/** Highlights persisted learning vocabulary/phrases. Chinese is linked exclusively through the
 * exact contextual subtitle span returned by the LLM; dictionary definitions are never used for
 * transcript alignment. */
object TranscriptWordAligner {
    private val englishParts = Regex("[A-Za-z]+(?:['’-][A-Za-z]+)*|\\d+(?:[.,]\\d+)*|[^\\s]")
    private val englishWord = Regex("[A-Za-z0-9].*")
    private val chineseParts = Regex("[\\p{IsHan}]|[A-Za-z0-9]+|[^\\s]")
    private val chineseContent = Regex("[\\p{IsHan}A-Za-z0-9]+")
    private data class HighlightCandidate(
        val highlight: TranscriptHighlight,
        val englishRange: IntRange,
        val chineseRanges: List<IntRange>,
    )

    fun align(
        english: String?,
        chinese: String?,
        highlights: List<TranscriptHighlight> = emptyList(),
    ): AlignedTranscript {
        val enText = english.orEmpty()
        val zhText = chinese.orEmpty()
        val enMatches = englishParts.findAll(enText).toList()
        val zhMatches = chineseParts.findAll(zhText).toList()
        val contentIndexes = enMatches.indices.filter { englishWord.matches(enMatches[it].value) }
        val assignedEnglish = arrayOfNulls<Int>(enMatches.size)
        val assignedChinese = Array(zhMatches.size) { linkedSetOf<Int>() }
        val windows = mutableListOf<PlaybackWindow>()
        val highlightAlignmentIds = mutableMapOf<String, Int>()
        val occupiedTokenIndexes = mutableSetOf<Int>()
        val candidates = highlights.mapNotNull { highlight ->
            val range = findEnglishRange(enText, highlight.surfaceForm, highlight.englishOccurrence) ?: return@mapNotNull null
            val chineseRanges = highlight.chineseCandidates
                .sortedByDescending(String::length)
                .mapNotNull { candidate -> findChineseRange(zhText, candidate) }
                .distinct()
            HighlightCandidate(highlight, range, chineseRanges)
        }.sortedByDescending { it.englishRange.last - it.englishRange.first }
            .filter { candidate ->
                val tokenIndexes = enMatches.indices.filter { enMatches[it].range overlaps candidate.englishRange }
                val accepted = tokenIndexes.isNotEmpty() && tokenIndexes.none(occupiedTokenIndexes::contains)
                if (accepted) occupiedTokenIndexes += tokenIndexes
                accepted
            }
            .sortedBy { it.englishRange.first }

        candidates.forEachIndexed { alignmentId, candidate ->
            val matchedTokenIndexes = enMatches.indices.filter { enMatches[it].range overlaps candidate.englishRange }
            matchedTokenIndexes.forEach { assignedEnglish[it] = alignmentId }
            highlightAlignmentIds[candidate.highlight.id] = alignmentId

            val wordOrdinals = matchedTokenIndexes.mapNotNull { index -> contentIndexes.indexOf(index).takeIf { it >= 0 } }
            if (wordOrdinals.isNotEmpty() && contentIndexes.isNotEmpty()) {
                windows += PlaybackWindow(
                    alignmentId,
                    wordOrdinals.min().toFloat() / contentIndexes.size,
                    (wordOrdinals.max() + 1f) / contentIndexes.size,
                )
            }

            candidate.chineseRanges.forEach { chineseRange ->
                zhMatches.indices.filter { zhMatches[it].range overlaps chineseRange }.forEach { assignedChinese[it] += alignmentId }
            }
        }

        return AlignedTranscript(
            english = enMatches.mapIndexed { index, match ->
                DisplayToken(text = match.value, alignmentId = assignedEnglish[index], isWord = englishWord.matches(match.value))
            }.mergeAdjacentAlignedTokens(joiner = " "),
            chinese = zhMatches.mapIndexed { index, match ->
                val ids = assignedChinese[index].toSet()
                DisplayToken(match.value, ids.firstOrNull(), ids, chineseContent.matches(match.value))
            }
                .mergeAdjacentAlignedTokens(joiner = ""),
            playbackWindows = windows,
            highlightAlignmentIds = highlightAlignmentIds,
        )
    }

    fun alignmentIdFor(highlights: List<TranscriptHighlight>, highlightId: String): Int? =
        highlights.sortedByDescending { it.surfaceForm.length }.indexOfFirst { it.id == highlightId }.takeIf { it >= 0 }

    fun activeAlignmentId(
        aligned: AlignedTranscript,
        positionMs: Long,
        cueStartMs: Long,
        cueEndMs: Long,
    ): Int? {
        if (positionMs < cueStartMs || positionMs >= cueEndMs) return null
        val duration = (cueEndMs - cueStartMs).coerceAtLeast(1L)
        val fraction = (positionMs - cueStartMs).toFloat() / duration
        return aligned.playbackWindows
            .sortedBy { it.startFraction }
            .lastOrNull { fraction >= it.startFraction }
            ?.alignmentId
    }

    private fun findEnglishRange(text: String, phrase: String, occurrence: Int = 0): IntRange? {
        if (phrase.isBlank()) return null
        val escaped = phrase.trim().split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }
        return Regex("(?i)(?<![A-Za-z0-9])$escaped(?![A-Za-z0-9])")
            .findAll(text)
            .elementAtOrNull(occurrence.coerceAtLeast(0))
            ?.range
    }

    private fun findChineseRange(text: String, candidate: String): IntRange? {
        val cleaned = candidate.trim().removePrefix("指").trim()
        if (cleaned.isBlank()) return null
        val start = text.indexOf(cleaned)
        return start.takeIf { it >= 0 }?.let { it until it + cleaned.length }
    }

    private infix fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last

    private fun List<DisplayToken>.mergeAdjacentAlignedTokens(joiner: String): List<DisplayToken> = buildList {
        this@mergeAdjacentAlignedTokens.forEach { token ->
            val previous = lastOrNull()
            if (token.alignmentIds.isNotEmpty() && previous?.alignmentIds == token.alignmentIds && previous.isWord && token.isWord) {
                removeAt(lastIndex)
                add(previous.copy(text = previous.text + joiner + token.text))
            } else add(token)
        }
    }
}
