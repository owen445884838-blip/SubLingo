package com.sublingo.app.data.vocabulary

import com.sublingo.app.data.remote.DictionaryEntry
import com.sublingo.app.data.remote.DictionarySense
import com.sublingo.app.data.remote.deriveDictionaryEntry
import com.sublingo.app.data.remote.dictionaryFallbackForms
import com.sublingo.app.data.remote.inferPartOfSpeech
import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryMeaningPolicyTest {
    @Test fun currentStandardDictionarySourceIsVersionedAndStable() {
        assertEquals("standard-en-zh-v2", STANDARD_DICTIONARY_SOURCE)
    }

    @Test fun infersUsefulPartOfSpeechFromStandardDictionaryAbbreviations() {
        assertEquals("verb / noun", inferPartOfSpeech("vt. 得到；vi. 到达；n. 救球"))
        assertEquals("adjective", inferPartOfSpeech("a. 新的；s. 崭新的"))
    }

    @Test fun adverbFallsBackToItsBundledDictionaryBaseForm() {
        assertEquals(listOf("collective"), dictionaryFallbackForms("collectively"))
        val derived = deriveDictionaryEntry(
            word = "collectively",
            base = "collective",
            entry = DictionaryEntry(
                phonetic = null,
                audioUrl = null,
                senses = listOf(DictionarySense("adjective", "forming a whole", "集体的, 共同的")),
            ),
        )!!

        assertEquals("adverb", derived.senses.single().pos)
        assertEquals("集体地；共同地", derived.senses.single().definitionZh)
    }
}
