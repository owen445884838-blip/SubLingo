package com.sublingo.app.data.vocabulary

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VocabularyDifficulty(
    val rank: Int,
    val label: String,
    val cefrLabel: String,
    val description: String,
) {
    A1(1, "入门", "A1", "保留入门及以上词汇"),
    A2(2, "初级", "A2", "过滤最基础词汇"),
    B1(3, "中级", "B1", "聚焦中级词汇与常用短语"),
    B2(4, "中高级", "B2", "聚焦进阶词汇与固定搭配"),
    C1(5, "高级", "C1–C2", "只保留高级词汇与完整表达"),
    C2(6, "精通", "C2", "仅用于提取结果分级"),
    UNKNOWN(0, "未分级", "未分级", "尚未可靠分级"),
    ;

    fun meets(minimum: VocabularyDifficulty): Boolean = this == UNKNOWN || rank >= minimum.rank

    companion object {
        val selectable = listOf(A1, A2, B1, B2, C1)

        fun parse(raw: String?): VocabularyDifficulty = entries.firstOrNull {
            it.name.equals(raw?.trim(), ignoreCase = true)
        } ?: UNKNOWN
    }
}

object VocabularyDifficultyClassifier {
    private val a1 = setOf(
        "able", "after", "again", "all", "always", "answer", "ask", "back", "bad", "be", "because",
        "before", "big", "book", "boy", "call", "can", "car", "child", "come", "day", "do", "down",
        "eat", "family", "feel", "find", "first", "friend", "get", "give", "go", "good", "great", "have",
        "help", "home", "house", "know", "learn", "like", "little", "live", "look", "love", "make", "man",
        "many", "more", "new", "night", "old", "one", "people", "play", "right", "say", "school", "see",
        "show", "small", "take", "talk", "tell", "thank", "thing", "think", "time", "today", "use", "video",
        "want", "way", "week", "well", "woman", "work", "year",
    )
    private val a2 = setOf(
        "accept", "actually", "advice", "agree", "allow", "arrive", "believe", "borrow", "careful", "change",
        "choose", "decide", "different", "enough", "explain", "famous", "future", "happen", "important", "invite",
        "maybe", "often", "possible", "problem", "remember", "seem", "spend", "usually", "without",
    )
    private val b1 = setOf(
        "achieve", "advantage", "appreciate", "avoid", "behavior", "challenge", "consider", "continue", "develop",
        "experience", "improve", "include", "instead", "manage", "opportunity", "probably", "provide", "realize",
        "recommend", "relationship", "require", "responsible", "situation", "suggest",
    )
    private val phraseOverrides = mapOf(
        "by the way" to VocabularyDifficulty.A2,
        "thank you for coming" to VocabularyDifficulty.A2,
        "a lot" to VocabularyDifficulty.A1,
        "lot of time" to VocabularyDifficulty.A2,
        "do appreciate" to VocabularyDifficulty.B1,
        "take for granted" to VocabularyDifficulty.B2,
        "take it for granted" to VocabularyDifficulty.B2,
        "take into account" to VocabularyDifficulty.B2,
    )

    fun classify(
        surfaceForm: String,
        lemma: String = surfaceForm,
        itemType: VocabularyItemType = VocabularyItemType.WORD,
        llmLevel: String? = null,
    ): VocabularyDifficulty {
        val normalized = lemma.lowercase().trim().replace(Regex("\\s+"), " ")
        phraseOverrides[normalized]?.let { return it }
        // A model can overrate a very common word because it appears in a technical sentence.
        // Strong local anchors win for standalone words; phrases retain their contextual LLM level.
        if (itemType == VocabularyItemType.WORD && ' ' !in normalized) {
            when {
                normalized in a1 -> return VocabularyDifficulty.A1
                normalized in a2 -> return VocabularyDifficulty.A2
                normalized in b1 -> return VocabularyDifficulty.B1
            }
        }
        val supplied = VocabularyDifficulty.parse(llmLevel)
        if (supplied != VocabularyDifficulty.UNKNOWN) return supplied
        if (itemType != VocabularyItemType.WORD || ' ' in normalized) {
            val words = normalized.split(' ').size
            return when (itemType) {
                VocabularyItemType.IDIOM -> VocabularyDifficulty.B2
                VocabularyItemType.GRAMMATICAL_CHUNK,
                VocabularyItemType.DISCOURSE_MARKER,
                VocabularyItemType.FORMULAIC_EXPRESSION -> VocabularyDifficulty.B1
                VocabularyItemType.PHRASAL_VERB -> if (words >= 4) VocabularyDifficulty.B2 else VocabularyDifficulty.B1
                else -> if (words >= 4) VocabularyDifficulty.B2 else VocabularyDifficulty.B1
            }
        }
        return when {
            normalized.length >= 12 -> VocabularyDifficulty.C1
            normalized.length >= 9 -> VocabularyDifficulty.B2
            normalized.length >= 7 -> VocabularyDifficulty.B1
            else -> VocabularyDifficulty.A2
        }
    }
}

@Singleton
class VocabularyDifficultyPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("vocabulary_difficulty", Context.MODE_PRIVATE)
    private val mutableMinimum = MutableStateFlow(
        VocabularyDifficulty.parse(preferences.getString(KEY_MINIMUM, VocabularyDifficulty.B1.name))
            .takeIf { it in VocabularyDifficulty.selectable } ?: VocabularyDifficulty.B1,
    )
    val minimum: StateFlow<VocabularyDifficulty> = mutableMinimum.asStateFlow()

    fun setMinimum(value: VocabularyDifficulty) {
        if (value !in VocabularyDifficulty.selectable || mutableMinimum.value == value) return
        preferences.edit().putString(KEY_MINIMUM, value.name).apply()
        mutableMinimum.value = value
    }

    private companion object { const val KEY_MINIMUM = "minimum_cefr" }
}
