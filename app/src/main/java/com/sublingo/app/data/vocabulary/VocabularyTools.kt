package com.sublingo.app.data.vocabulary

import com.sublingo.app.data.db.SubtitleCueEntity
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class VocabularyCandidate(val surfaceForm: String, val normalized: String, val cueId: String, val frequency: Int)

enum class VocabularyItemType {
    WORD,
    COLLOCATION,
    PHRASAL_VERB,
    IDIOM,
    FORMULAIC_EXPRESSION,
    DISCOURSE_MARKER,
    GRAMMATICAL_CHUNK,
}

/**
 * Semantic version of the vocabulary/phrase extraction contract. This is deliberately separate
 * from the Room schema version: changing the LLM contract must refresh persisted occurrences, but
 * does not require a database migration when the existing itemType text column can store the new
 * values.
 */
object VocabularyPipelineContract {
    const val VERSION = 13
    const val MINIMUM_AUTO_REFRESH_VERSION = 10
    const val REFRESH_WORK_PREFIX = "transcript-vocabulary-v12-word-first"

    fun extractionPrompt(payload: String): String =
        "For every supplied bilingual subtitle cue, extract useful standalone English content words as the primary learning units. " +
            "Prioritize complete word coverage, accurate dictionary lemmas, and one WORD item per useful word occurrence. Exclude standalone basic function words. " +
            "Multi-word items are optional: return only unmistakable conventional phrasal verbs, fixed collocations, or idioms whose meaning/form should be learned together. " +
            "Do not extract routine compositional chunks, arbitrary verb-object spans, discourse fillers, whole clauses, or ordinary adjacent words as phrases. " +
            "A phrase must not suppress useful constituent WORD items; word learning takes priority. surfaceForm must be copied verbatim from sourceTextEn. " +
            "Classify every item with exactly one itemType: WORD, COLLOCATION, PHRASAL_VERB, or IDIOM. " +
            "lemma is the normalized dictionary form of the word or the canonical form of a genuine fixed phrase. " +
            "For every extracted item, translationZh must be the shortest exact contiguous corresponding substring copied verbatim from that same cue's sourceTextZh, " +
            "or null only when no reliable correspondence exists. Do not return dictionary definitions; this task is subtitle-span alignment. " +
            "Assign cefrLevel to the complete expression as used in context, using exactly A1, A2, B1, B2, C1, or C2. " +
            "Do not raise a basic word merely because the topic is technical, and do not derive a phrase level only from its hardest component word. " +
            "Return JSON array [{surfaceForm,lemma,itemType,sourceCueId,translationZh,cefrLevel}]. Cues: $payload"

    fun phraseAuditPrompt(payload: String): String =
        "Perform a phrase-coverage audit of every supplied bilingual subtitle cue. Do not extract standalone words. " +
            "Return every meaningful multi-word English unit that should be learned and highlighted together with one contextual Chinese span. " +
            "You must actively check for: (1) complete social/politeness formulas, for example 'Thank you for coming'; " +
            "(2) discourse markers/connectors, for example 'by the way'; " +
            "(3) emphatic auxiliary constructions, for example 'do appreciate', 'did enjoy', or 'does matter'; " +
            "(4) collocations, phrasal verbs, idioms, verb-object chunks, and prepositional complements; " +
            "(5) any adjacent words translated as one non-compositional or continuous Chinese semantic unit. " +
            "Choose the longest natural reusable unit, never an isolated fragment and never an arbitrary entire sentence. " +
            "surfaceForm must be copied verbatim from sourceTextEn. translationZh must be the shortest exact contiguous substring copied verbatim from the matching sourceTextZh. " +
            "Do not return a contained shorter phrase when the complete phrase is present. " +
            "Use exactly one itemType from COLLOCATION, PHRASAL_VERB, IDIOM, FORMULAIC_EXPRESSION, DISCOURSE_MARKER, or GRAMMATICAL_CHUNK. " +
            "lemma must represent the complete phrase. Assign cefrLevel to the complete expression using exactly A1, A2, B1, B2, C1, or C2. " +
            "Return only cues where at least one phrase is found; omit cues with no phrase. " +
            "Return [{sourceCueId,items:[{surfaceForm,lemma,itemType,translationZh,cefrLevel}]}]. Cues: $payload"
}

/** WorkManager can restore the main pipeline while the transcript screen requests a version
 * refresh under a different unique-work name. Serialize vocabulary work per video in-process so a
 * second worker waits for the first and then observes the completed vocabulary version. */
object VocabularyExecutionGate {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withVideoLock(videoId: String, block: suspend () -> T): T {
        val lock = locks.getOrPut(videoId) { Mutex() }
        return lock.withLock { block() }
    }
}

data class VocabularyLlmPolicy(
    val cuesPerBatch: Int,
    val maxRequestsPerRun: Int,
    val maxSplitDepth: Int,
    val maxAttemptsPerInput: Int,
    val remoteExtractionEnabled: Boolean,
) {
    fun <T> batches(items: List<T>): List<List<T>> = items.chunked(cuesPerBatch)

    companion object {
        private val DEFAULT = VocabularyLlmPolicy(
            cuesPerBatch = 60,
            maxRequestsPerRun = 16,
            maxSplitDepth = 2,
            maxAttemptsPerInput = 2,
            remoteExtractionEnabled = true,
        )
        private val XIAOMI_MIMO = DEFAULT.copy(
            cuesPerBatch = 24,
            remoteExtractionEnabled = false,
        )

        fun forPreset(presetId: String?): VocabularyLlmPolicy =
            if (presetId == "xiaomi-mimo") XIAOMI_MIMO else DEFAULT
    }
}

class VocabularyRequestBudget(private val limit: Int) {
    var used: Int = 0
        private set
    var isDisabled: Boolean = false
        private set

    fun tryAcquire(): Boolean {
        if (isDisabled || used >= limit) return false
        used++
        return true
    }

    fun disable() {
        isDisabled = true
    }
}

object ContextualChineseMeaningResolver {
    private val hanTerm = Regex("[\\u3400-\\u9FFF]{2,}")
    private val genericTerms = setOf("一个", "一种", "表示", "用于", "进行", "东西", "事情", "有关", "关于")

    fun resolve(
        contextZh: String?,
        alignedMeaningZh: String?,
        definitionZh: String?,
        alignedCandidatesZh: Collection<String> = emptyList(),
        sourceTerms: Collection<String> = emptyList(),
    ): String? {
        val context = contextZh?.trim().orEmpty()
        if (context.isEmpty()) return null
        alignedMeaningZh?.trim()?.takeIf { it.isNotEmpty() && context.contains(it) }?.let { return it }

        alignedCandidatesZh.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filter(context::contains)
            .maxByOrNull(String::length)
            ?.let { return it }

        sourceTerms.asSequence()
            .map(String::trim)
            .filter { it.length >= 2 && it.any(Char::isLetterOrDigit) }
            .sortedByDescending(String::length)
            .firstNotNullOfOrNull { term ->
                Regex("(?i)(?<![A-Za-z0-9])${Regex.escape(term)}(?![A-Za-z0-9])")
                    .find(context)?.value
            }
            ?.let { return it }

        return definitionZh.orEmpty()
            .split(Regex("[,，;；、/|\\n]+"))
            .flatMap { segment -> hanTerm.findAll(segment.replace(Regex("[（(][^）)]*[）)]"), "")).map { it.value } }
            .filter { it !in genericTerms && context.contains(it) }
            .distinct()
            .maxWithOrNull(compareBy<String> { it.length }.thenByDescending { definitionZh.orEmpty().indexOf(it) })
    }
}

object PhraseAuditPlanner {
    const val BATCH_SIZE = 24

    private val strongSignals = Regex(
        "(?i)\\b(thank\\s+you|by\\s+the\\s+way|a\\s+lot|kind\\s+of|sort\\s+of|" +
            "you\\s+know|i\\s+mean|of\\s+course|at\\s+all|as\\s+well|" +
            "do\\s+\\w+|did\\s+\\w+|does\\s+\\w+|" +
            "\\w+\\s+(up|out|off|on|over|away|back|through))\\b",
    )

    fun candidates(cues: List<SubtitleCueEntity>, alreadyCoveredCueIds: Set<String>): List<SubtitleCueEntity> =
        cues.filter { cue ->
            val words = Regex("[A-Za-z][A-Za-z'-]*").findAll(cue.text).map { it.value }.toList()
            val strong = strongSignals.containsMatchIn(cue.text)
            // A strong signal can contain several distinct chunks in one cue, so keep auditing it
            // even if extraction already found one phrase. Only skip already-covered, merely-long
            // cues whose risk comes from generic sentence shape.
            words.size >= 3 && (
                strong ||
                    (cue.id !in alreadyCoveredCueIds && words.size >= 8 &&
                        words.any { it.lowercase(Locale.US) in CONNECTORS })
                )
        }

    private val CONNECTORS = setOf(
        "for", "to", "of", "with", "about", "from", "into", "after", "before", "through", "while",
    )
}

/** Parses the JSON shapes commonly produced by OpenAI-compatible LLMs without weakening any
 * vocabulary validation. In particular, long outputs are sometimes returned as JSONL/object
 * sequences without the requested outer array. */
object LlmJsonResponseParser {
    fun array(raw: String, label: String): JsonArray {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        parseElement(cleaned)?.let(::toArray)?.let { return it }

        val firstBracket = cleaned.indexOf('[')
        val lastBracket = cleaned.lastIndexOf(']')
        if (firstBracket >= 0 && lastBracket >= firstBracket) {
            parseElement(cleaned.substring(firstBracket, lastBracket + 1))
                ?.let(::toArray)
                ?.let { return it }
        }

        val objects = extractBalancedObjects(cleaned).mapNotNull(::parseElement)
        if (objects.isNotEmpty()) {
            if (objects.size == 1) toArray(objects.single())?.let { return it }
            return JsonArray(objects)
        }

        val shape = when {
            cleaned.isBlank() -> "empty"
            cleaned.startsWith('{') -> "object"
            cleaned.startsWith('<') -> "markup"
            else -> "text"
        }
        error("$label response has no parseable JSON array or object sequence (shape=$shape, chars=${cleaned.length})")
    }

    private fun parseElement(value: String): JsonElement? =
        runCatching { Json.parseToJsonElement(value) }.getOrNull()

    private fun toArray(element: JsonElement): JsonArray? = when (element) {
        is JsonArray -> element
        is JsonObject -> listOf("items", "results", "vocabulary", "data")
            .firstNotNullOfOrNull { key -> element[key] as? JsonArray }
            ?: JsonArray(listOf(element))
        else -> null
    }

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

object VocabularyPreprocessor {
    private val word = Regex("[A-Za-z][A-Za-z'-]+")
    private val stopWords = setOf(
        "the", "and", "that", "this", "with", "from", "have", "has", "had", "you", "your", "they", "their",
        "there", "here", "what", "when", "where", "which", "would", "could", "should", "will", "just", "like",
        "about", "into", "than", "then", "them", "were", "was", "are", "is", "been", "being", "for", "not",
        "but", "can", "all", "our", "out", "how", "why", "who", "its", "it's", "don't", "did", "does",
        "of", "to", "in", "on", "at", "by", "as", "or", "if", "we", "he", "she", "it", "an", "be", "do",
    )

    fun extract(cues: List<SubtitleCueEntity>): List<VocabularyCandidate> {
        val occurrences = linkedMapOf<String, MutableList<Pair<String, String>>>()
        cues.forEach { cue ->
            word.findAll(cue.text).forEach { match ->
                val surface = match.value
                val normalized = normalize(surface)
                if (normalized !in stopWords && normalized.length >= 2) {
                    occurrences.getOrPut(normalized) { mutableListOf() }.add(surface to cue.id)
                }
            }
        }
        return occurrences.entries
            .sortedWith(compareByDescending<Map.Entry<String, MutableList<Pair<String, String>>>> { it.value.size }.thenByDescending { it.key.length })
            .map { (normalized, items) -> VocabularyCandidate(items.first().first, normalized, items.first().second, items.size) }
    }

    fun normalize(value: String): String = value.lowercase(Locale.US).trim('\'', '-').let { raw ->
        when {
            shouldPreserveTrailingS(raw) -> raw
            raw.endsWith("ated") && raw.length > 5 -> raw.dropLast(1)
            raw.endsWith("ied") && raw.length > 4 -> raw.dropLast(3) + "y"
            raw.endsWith("ies") && raw.length > 4 -> raw.dropLast(3) + "y"
            raw.endsWith("ing") && raw.length > 5 -> raw.dropLast(3)
            raw.endsWith("ed") && raw.length > 4 -> raw.dropLast(2)
            raw.endsWith("s") && !raw.endsWith("ss") && raw.length > 3 -> raw.dropLast(1)
            else -> raw
        }
    }

    private fun shouldPreserveTrailingS(word: String): Boolean =
        word in setOf("news", "series", "species") ||
            listOf("us", "is", "ss", "ous", "ics").any(word::endsWith)
}

object VocabularyLemmaRepairPolicy {
    private val irregularForms = mapOf(
        "brought" to "bring",
        "bought" to "buy",
        "came" to "come",
        "coming" to "come",
        "done" to "do",
        "gave" to "give",
        "given" to "give",
        "gone" to "go",
        "made" to "make",
        "making" to "make",
        "ran" to "run",
        "running" to "run",
        "said" to "say",
        "saw" to "see",
        "seen" to "see",
        "singing" to "sing",
        "taken" to "take",
        "took" to "take",
        "went" to "go",
        "written" to "write",
        "wrote" to "write",
    )

    /**
     * Ordered dictionary-form candidates for a subtitle surface. The bundled dictionary decides
     * between ambiguous spellings; this method only supplies morphologically plausible options.
     */
    fun dictionaryLemmaCandidates(surfaceForm: String): List<String> {
        val surface = surfaceForm.lowercase(Locale.US).trim('\'', '-')
        if (surface.isBlank() || surface.any { !it.isLetter() && it != '\'' && it != '-' }) return emptyList()
        return buildList {
            irregularForms[surface]?.let(::add)
            when {
                surface.endsWith("ied") && surface.length > 4 -> add(surface.dropLast(3) + "y")
                surface.endsWith("ing") && surface.length > 5 -> {
                    val stem = surface.dropLast(3)
                    if (stem.length >= 2 && stem.last() == stem[stem.lastIndex - 1]) add(stem.dropLast(1))
                    add(stem + "e")
                    add(stem)
                }
                surface.endsWith("ed") && surface.length > 4 -> {
                    val stem = surface.dropLast(2)
                    // Dropping only the final d restores a silent e: eliminated -> eliminate.
                    add(surface.dropLast(1))
                    if (stem.length >= 2 && stem.last() == stem[stem.lastIndex - 1]) add(stem.dropLast(1))
                    add(stem)
                }
                surface.endsWith("ies") && surface.length > 4 -> add(surface.dropLast(3) + "y")
                surface.endsWith("es") && surface.length > 4 -> {
                    add(surface.dropLast(1))
                    add(surface.dropLast(2))
                }
                surface.endsWith("s") && surface.length > 3 -> add(surface.dropLast(1))
            }
            add(VocabularyPreprocessor.normalize(surface))
            add(surface)
        }.filter { it.length >= 2 }.distinct()
    }

    fun correctionCandidates(lemma: String, surfaceForms: Collection<String>): List<String> {
        val normalizedLemma = lemma.lowercase(Locale.US).trim()
        return buildList {
            correctedLegacyLemma(normalizedLemma, surfaceForms)?.let(::add)
            surfaceForms.forEach { surface ->
                val candidates = dictionaryLemmaCandidates(surface)
                val currentIndex = candidates.indexOf(normalizedLemma)
                if (currentIndex > 0) addAll(candidates.take(currentIndex))
            }
        }.distinct()
    }

    fun correctedLegacyLemma(lemma: String, surfaceForms: Collection<String>): String? {
        val normalizedLemma = lemma.lowercase(Locale.US).trim()
        return surfaceForms.asSequence()
            .map { it.lowercase(Locale.US).trim('\'', '-') }
            .filter { surface ->
                surface.length > normalizedLemma.length &&
                    surface.dropLast(1) == normalizedLemma &&
                    VocabularyPreprocessor.normalize(surface) == surface
            }
            .distinct()
            .singleOrNull()
    }
}

object VocabularyLexemeIdentity {
    fun resolve(normalizedLemma: String, existingId: String?): String =
        existingId ?: "lexeme-en-${normalizedLemma.hashCode().toUInt().toString(16)}"
}

data class SelectedVocabulary(
    val surfaceForm: String,
    val lemma: String,
    val sourceCueId: String,
    val translationZh: String? = null,
    val definitionZh: String? = null,
    val itemType: VocabularyItemType = VocabularyItemType.WORD,
    val difficultyLevel: VocabularyDifficulty = VocabularyDifficulty.UNKNOWN,
    val difficultySource: String = "LOCAL",
    val difficultyConfidence: Float = 0f,
)

object VocabularySelection {
    fun sanitize(
        selected: List<SelectedVocabulary>,
        candidates: List<VocabularyCandidate>,
        validCueIds: Set<String> = candidates.mapTo(mutableSetOf()) { it.cueId },
        englishByCueId: Map<String, String> = emptyMap(),
        chineseByCueId: Map<String, String> = emptyMap(),
    ): List<SelectedVocabulary> {
        val candidateCueIds = candidates.associate { it.normalized to it.cueId }
        val validated = selected.asSequence()
            .map { item ->
                val lemma = if (' ' in item.lemma.trim()) {
                    item.lemma.lowercase(Locale.US).trim().replace(Regex("\\s+"), " ")
                } else {
                    VocabularyPreprocessor.normalize(item.lemma)
                }
                item.copy(
                    lemma = lemma,
                    sourceCueId = item.sourceCueId.takeIf(validCueIds::contains)
                        ?: if (englishByCueId.isEmpty()) {
                            candidateCueIds[VocabularyPreprocessor.normalize(item.surfaceForm)].orEmpty()
                        } else "",
                )
            }
            .filter { item ->
                val words = item.surfaceForm.trim().split(Regex("\\s+"))
                val validShape = when (item.itemType) {
                    VocabularyItemType.WORD -> words.size == 1 &&
                        ' ' !in item.lemma &&
                        VocabularyPreprocessor.normalize(item.surfaceForm) in candidateCueIds &&
                        item.lemma in VocabularyLemmaRepairPolicy.dictionaryLemmaCandidates(item.surfaceForm)
                    else -> words.size >= 2 && words.any { VocabularyPreprocessor.normalize(it) in candidateCueIds }
                }
                val english = englishByCueId[item.sourceCueId]
                val chinese = chineseByCueId[item.sourceCueId]
                val exactEnglish = english == null || exactSurfaceRange(english, item.surfaceForm) != null
                val exactChinese = chineseByCueId.isEmpty() || item.translationZh.isNullOrBlank() || chinese?.contains(item.translationZh.trim()) == true
                item.lemma.isNotBlank() && item.sourceCueId.isNotBlank() && validShape && exactEnglish && exactChinese
            }
            .toList()
            // Extraction and the phrase-coverage audit may return the same surface with different
            // metadata. Prefer the variant with a validated Chinese span. Do not de-duplicate by
            // lemma here: a model may assign the same canonical lemma to a short fragment and its
            // complete phrase, and the overlap resolver below must see both so the longer unit wins.
            .groupBy { item -> item.sourceCueId to normalizedSurface(item.surfaceForm) }
            .values
            .map { variants ->
                variants.maxWithOrNull(
                    compareBy<SelectedVocabulary> { !it.translationZh.isNullOrBlank() }
                        .thenBy { it.itemType != VocabularyItemType.WORD }
                        .thenBy { it.lemma.length },
                )!!
            }
        if (englishByCueId.isEmpty()) {
            return validated.distinctBy { item -> item.sourceCueId to item.lemma }
        }
        return validated.groupBy(SelectedVocabulary::sourceCueId).values.flatMap { cueItems ->
            val english = englishByCueId[cueItems.first().sourceCueId].orEmpty()
            val occupied = mutableListOf<IntRange>()
            cueItems.sortedWith(
                compareByDescending<SelectedVocabulary> { it.itemType != VocabularyItemType.WORD }
                    .thenByDescending { exactSurfaceRange(english, it.surfaceForm)?.let { range -> range.last - range.first } ?: -1 },
            ).filter { item ->
                val range = exactSurfaceRange(english, item.surfaceForm) ?: return@filter false
                val overlaps = occupied.any { existing -> existing.first <= range.last && range.first <= existing.last }
                if (!overlaps) occupied += range
                !overlaps
            }
        }
    }

    fun exactSurfaceRange(text: String, surfaceForm: String): IntRange? {
        if (surfaceForm.isBlank()) return null
        val escaped = surfaceForm.trim().split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }
        return Regex("(?i)(?<![A-Za-z0-9])$escaped(?![A-Za-z0-9])").find(text)?.range
    }

    private fun normalizedSurface(value: String): String =
        value.lowercase(Locale.US).trim().replace(Regex("\\s+"), " ")
}
