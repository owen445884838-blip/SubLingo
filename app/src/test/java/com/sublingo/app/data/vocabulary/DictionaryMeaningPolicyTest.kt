package com.sublingo.app.data.vocabulary

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
}
