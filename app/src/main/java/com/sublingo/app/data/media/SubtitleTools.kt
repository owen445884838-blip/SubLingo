package com.sublingo.app.data.media

import com.sublingo.app.data.db.SubtitleCueEntity
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

data class TimedText(val sequence: Int, val startMs: Long, val endMs: Long, val text: String)

object SubtitleParser {
    private val timing = Regex("(\\d{1,2}:)?(\\d{2}):(\\d{2})[.,](\\d{3})\\s+-->\\s+(\\d{1,2}:)?(\\d{2}):(\\d{2})[.,](\\d{3})")

    fun parse(content: String): List<TimedText> {
        val lines = content.replace("\r", "").lines()
        val result = mutableListOf<TimedText>()
        var index = 0
        while (index < lines.size) {
            val match = timing.find(lines[index])
            if (match == null) { index++; continue }
            val text = buildList {
                index++
                while (index < lines.size && lines[index].isNotBlank()) {
                    add(cleanSubtitleText(lines[index]))
                    index++
                }
            }.filter(String::isNotBlank).distinct().joinToString(" ").trim()
            if (text.isNotBlank()) {
                result += TimedText(
                    sequence = result.size,
                    startMs = timestamp(match.groupValues[1], match.groupValues[2], match.groupValues[3], match.groupValues[4]),
                    endMs = timestamp(match.groupValues[5], match.groupValues[6], match.groupValues[7], match.groupValues[8]),
                    text = text,
                )
            }
        }
        return RollingSubtitleNormalizer.normalizeTimedText(result)
    }

    private fun cleanSubtitleText(value: String): String = value
        .replace(Regex("<\\d{2}:\\d{2}:\\d{2}[.,]\\d{3}>") , "")
        .replace(Regex("</?c(?:\\.[^>]*)?>"), "")
        .replace(Regex("</?(?:font|ruby|rt|v|lang)(?:\\s+[^>]*)?>"), "")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\uFEFF]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun timestamp(hour: String, minute: String, second: String, millis: String): Long {
        val h = hour.removeSuffix(":").toLongOrNull() ?: 0L
        return h * 3_600_000 + minute.toLong() * 60_000 + second.toLong() * 1_000 + millis.toLong()
    }
}

data class NormalizedSubtitleCue<T>(
    val source: T,
    val text: String,
    val removedPrefixFraction: Float = 0f,
)

/** Removes YouTube-style rolling caption snapshots without touching ordinary subtitle tracks. */
object RollingSubtitleNormalizer {
    private val token = Regex("[\\u3400-\\u9FFF]|\\[[^]]+]|>>|[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)?|[^\\s]")

    fun normalizeTimedText(cues: List<TimedText>): List<TimedText> = normalize(
        cues = cues,
        startMs = TimedText::startMs,
        endMs = TimedText::endMs,
        text = TimedText::text,
    ).mapIndexed { index, item -> item.source.copy(sequence = index, text = item.text) }

    fun normalizeEntities(cues: List<SubtitleCueEntity>): List<NormalizedSubtitleCue<SubtitleCueEntity>> = normalize(
        cues = cues,
        startMs = SubtitleCueEntity::startMs,
        endMs = SubtitleCueEntity::endMs,
        text = SubtitleCueEntity::text,
    )

    fun removeApproximatePrefix(value: String, fraction: Float): String {
        if (fraction <= 0f) return value
        val tokens = tokens(value)
        if (tokens.size < 2) return value
        val removeCount = (tokens.size * fraction).toInt().coerceIn(1, tokens.lastIndex)
        return value.substring(tokens[removeCount].range.first).trim()
    }

    private fun <T> normalize(
        cues: List<T>,
        startMs: (T) -> Long,
        endMs: (T) -> Long,
        text: (T) -> String,
    ): List<NormalizedSubtitleCue<T>> {
        if (cues.size < 3 || !looksRolling(cues, startMs, endMs, text)) {
            return cues.map { NormalizedSubtitleCue(it, text(it)) }
        }

        val retained = cues.filterIndexed { index, cue ->
            val duration = endMs(cue) - startMs(cue)
            if (duration > TRANSITION_MAX_MS) return@filterIndexed true
            val current = text(cue)
            val previous = cues.getOrNull(index - 1)?.let(text)
            val next = cues.getOrNull(index + 1)?.let(text)
            !listOfNotNull(previous, next).any { neighbor ->
                containsTokens(neighbor, current) || meaningfulOverlap(current, neighbor) > 0 || meaningfulOverlap(neighbor, current) > 0
            }
        }

        val cleaned = mutableListOf<NormalizedSubtitleCue<T>>()
        var previousRaw: String? = null
        retained.forEach { cue ->
            val current = text(cue)
            val currentTokens = tokens(current)
            val overlap = previousRaw?.let { overlap(it, current) } ?: 0
            val meaningful = overlap.takeIf { it > 0 && overlapTextLength(currentTokens, it) >= MIN_OVERLAP_CHARS } ?: 0
            val cleanedText = when {
                meaningful == 0 -> current.trim()
                meaningful >= currentTokens.size -> ""
                else -> current.substring(currentTokens[meaningful].range.first).trim()
            }
            if (cleanedText.isNotBlank()) {
                cleaned += NormalizedSubtitleCue(
                    source = cue,
                    text = cleanedText,
                    removedPrefixFraction = if (currentTokens.isEmpty()) 0f else meaningful.toFloat() / currentTokens.size,
                )
            }
            previousRaw = current
        }
        return cleaned
    }

    private fun <T> looksRolling(
        cues: List<T>,
        startMs: (T) -> Long,
        endMs: (T) -> Long,
        text: (T) -> String,
    ): Boolean {
        val tinyOverlaps = cues.indices.count { index ->
            val cue = cues[index]
            endMs(cue) - startMs(cue) <= TRANSITION_MAX_MS &&
                listOfNotNull(cues.getOrNull(index - 1), cues.getOrNull(index + 1)).any { neighbor ->
                    containsTokens(text(neighbor), text(cue)) || meaningfulOverlap(text(cue), text(neighbor)) > 0
                }
        }
        val boundaryOverlaps = cues.zipWithNext().count { (first, second) ->
            startMs(second) - endMs(first) <= BOUNDARY_GAP_MAX_MS && meaningfulOverlap(text(first), text(second)) > 0
        }
        return tinyOverlaps >= 2 && boundaryOverlaps >= 2
    }

    private fun meaningfulOverlap(previous: String, current: String): Int {
        val count = overlap(previous, current)
        return count.takeIf { overlapTextLength(tokens(current), it) >= MIN_OVERLAP_CHARS } ?: 0
    }

    private fun overlap(previous: String, current: String): Int {
        val before = tokens(previous).map { it.value.lowercase(Locale.US) }
        val after = tokens(current).map { it.value.lowercase(Locale.US) }
        for (size in minOf(before.size, after.size) downTo 1) {
            if (before.takeLast(size) == after.take(size)) return size
        }
        return 0
    }

    private fun containsTokens(container: String, candidate: String): Boolean {
        val inside = tokens(container).map { it.value.lowercase(Locale.US) }
        val sought = tokens(candidate).map { it.value.lowercase(Locale.US) }
        if (sought.isEmpty() || sought.size > inside.size) return false
        return inside.windowed(sought.size).any { it == sought }
    }

    private fun tokens(value: String) = token.findAll(value).toList()
    private fun overlapTextLength(tokens: List<MatchResult>, count: Int): Int =
        tokens.take(count).sumOf { match -> match.value.count(Char::isLetterOrDigit) }

    private const val TRANSITION_MAX_MS = 100L
    private const val BOUNDARY_GAP_MAX_MS = 120L
    private const val MIN_OVERLAP_CHARS = 4
}

fun List<TimedText>.toEntities(trackId: String): List<SubtitleCueEntity> = map {
    SubtitleCueEntity("$trackId-${it.sequence}", trackId, it.sequence, it.startMs, it.endMs, it.text)
}

object TranslationAlignment {
    data class Item(val index: Int, val text: String)

    fun validate(sourceIndexes: Set<Int>, translated: List<Item>): Set<Int> {
        val grouped = translated.groupBy { it.index }
        val invalid = sourceIndexes.filterTo(linkedSetOf()) { index ->
            val items = grouped[index]
            items == null || items.size != 1 || items.single().text.isBlank()
        }
        invalid.addAll(grouped.keys.filter { it !in sourceIndexes })
        return invalid
    }

    fun batches(
        cues: List<SubtitleCueEntity>,
        tokenBudget: Int = 2_400,
        maxBatchSize: Int = Int.MAX_VALUE,
    ): List<List<SubtitleCueEntity>> {
        val batches = mutableListOf<MutableList<SubtitleCueEntity>>()
        var current = mutableListOf<SubtitleCueEntity>()
        var estimate = 0
        cues.forEach { cue ->
            val cost = (cue.text.length / 3).coerceAtLeast(1) + 16
            if (current.isNotEmpty() && (estimate + cost > tokenBudget || current.size >= maxBatchSize)) {
                batches += current
                current = mutableListOf()
                estimate = 0
            }
            current += cue
            estimate += cost
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }

    fun batchesForProvider(
        cues: List<SubtitleCueEntity>,
        presetId: String,
    ): List<List<SubtitleCueEntity>> = batches(
        cues = cues,
        tokenBudget = when (presetId) {
            XIAOMI_MIMO_PRESET_ID -> XIAOMI_TRANSLATION_TOKEN_BUDGET
            DEEPSEEK_PRESET_ID -> DEEPSEEK_TRANSLATION_TOKEN_BUDGET
            else -> DEFAULT_TRANSLATION_TOKEN_BUDGET
        },
        maxBatchSize = when (presetId) {
            XIAOMI_MIMO_PRESET_ID -> XIAOMI_TRANSLATION_MAX_BATCH_SIZE
            DEEPSEEK_PRESET_ID -> DEEPSEEK_TRANSLATION_MAX_BATCH_SIZE
            else -> Int.MAX_VALUE
        },
    )

    private const val XIAOMI_MIMO_PRESET_ID = "xiaomi-mimo"
    private const val DEEPSEEK_PRESET_ID = "deepseek"
    private const val XIAOMI_TRANSLATION_TOKEN_BUDGET = 220
    private const val XIAOMI_TRANSLATION_MAX_BATCH_SIZE = 1
    private const val DEEPSEEK_TRANSLATION_TOKEN_BUDGET = 800
    private const val DEEPSEEK_TRANSLATION_MAX_BATCH_SIZE = 12
    private const val DEFAULT_TRANSLATION_TOKEN_BUDGET = 2_400
}

/**
 * Parses translation output one JSON object at a time. Some OpenAI-compatible models occasionally
 * emit one malformed object inside an otherwise usable array. Parsing the whole array would discard
 * every valid translation and bypass [TranslationAlignment]'s targeted missing-index retry.
 */
data class TranslationWordPair(val english: String, val chinese: String, val englishOccurrence: Int = 0)
data class ParsedTranslation(
    val item: TranslationAlignment.Item,
    val wordPairs: List<TranslationWordPair>,
)

object TranslationWordMapRepair {
    private data class PositionedPair(
        val start: Int,
        val endExclusive: Int,
        val pair: TranslationWordPair,
    )

    fun fillUncoveredChinese(
        sourceText: String,
        parsed: ParsedTranslation,
    ): ParsedTranslation {
        if (sourceText.isBlank() || parsed.item.text.isBlank()) return parsed
        val candidates = parsed.wordPairs
            .filter { pair ->
                pair.english.isNotBlank() &&
                    pair.chinese.isNotBlank() &&
                    containsEnglishSurface(sourceText, pair.english) &&
                    parsed.item.text.contains(pair.chinese)
            }
            .distinctBy { pair -> Triple(pair.english.lowercase(Locale.US), pair.chinese, pair.englishOccurrence) }
            .sortedByDescending { it.chinese.length }
        val covered = BooleanArray(parsed.item.text.length)
        val positionedPairs = mutableListOf<PositionedPair>()
        candidates.forEach { pair ->
            var searchStart = 0
            while (searchStart < parsed.item.text.length) {
                val start = parsed.item.text.indexOf(pair.chinese, searchStart)
                if (start < 0) break
                val endExclusive = start + pair.chinese.length
                if ((start until endExclusive).none { covered[it] }) {
                    for (index in start until endExclusive) covered[index] = true
                    positionedPairs += PositionedPair(start, endExclusive, pair)
                    break
                }
                searchStart = start + 1
            }
        }
        val exactPairs = positionedPairs.toList()
        var index = 0
        while (index < parsed.item.text.length) {
            if (!parsed.item.text[index].isLetterOrDigit() || covered[index]) {
                index++
                continue
            }
            val start = index
            while (index < parsed.item.text.length && parsed.item.text[index].isLetterOrDigit() && !covered[index]) index++
            val localAnchor = exactPairs.minWithOrNull(
                compareBy<PositionedPair> { positioned ->
                    when {
                        positioned.endExclusive <= start -> start - positioned.endExclusive
                        positioned.start >= index -> positioned.start - index
                        else -> 0
                    }
                }.thenBy { positioned -> if (positioned.start >= index) 0 else 1 },
            )?.pair
            positionedPairs += PositionedPair(
                start = start,
                endExclusive = index,
                pair = TranslationWordPair(
                    english = localAnchor?.english ?: sourceText.trim(),
                    chinese = parsed.item.text.substring(start, index),
                    englishOccurrence = localAnchor?.englishOccurrence ?: 0,
                ),
            )
        }
        return parsed.copy(
            wordPairs = positionedPairs.sortedBy { it.start }.map { it.pair },
        )
    }

    private fun containsEnglishSurface(text: String, surface: String): Boolean {
        val escaped = surface.trim().split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }
        return Regex("(?i)(?<![A-Za-z0-9])$escaped(?![A-Za-z0-9])").containsMatchIn(text)
    }
}

object TranslationResponseParser {
    fun parse(raw: String): List<TranslationAlignment.Item> = parseAligned(raw).map(ParsedTranslation::item)

    fun parseAligned(raw: String): List<ParsedTranslation> = extractBalancedObjects(stripFence(raw))
        .mapNotNull { objectText ->
            val item = runCatching { Json.parseToJsonElement(objectText).jsonObject }.getOrNull()
                ?: return@mapNotNull null
            val index = item["index"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val text = item["textZh"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val pairs = item["wordPairs"]?.let { element ->
                runCatching {
                    element.jsonArray.mapNotNull { pairElement ->
                        val pair = pairElement.jsonObject
                        val english = pair["en"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                        val chinese = pair["zh"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                        val occurrence = pair["occurrence"]?.jsonPrimitive?.intOrNull ?: 0
                        TranslationWordPair(english, chinese, occurrence.coerceAtLeast(0))
                            .takeIf { english.isNotBlank() && chinese.isNotBlank() }
                    }
                }.getOrDefault(emptyList<TranslationWordPair>())
            }.orEmpty()
            ParsedTranslation(TranslationAlignment.Item(index, text), pairs)
        }

    private fun stripFence(raw: String): String = raw.trim()
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    private fun extractBalancedObjects(value: String): List<String> {
        val objects = mutableListOf<String>()
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false
        value.forEachIndexed { index, char ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{' -> {
                        if (depth == 0) start = index
                        depth++
                    }
                    '}' -> if (depth > 0) {
                        depth--
                        if (depth == 0 && start >= 0) {
                            objects += value.substring(start, index + 1)
                            start = -1
                        }
                    }
                }
            }
        }
        return objects
    }
}

fun normalizeLanguage(value: String): String = value.lowercase(Locale.US).replace('_', '-')
