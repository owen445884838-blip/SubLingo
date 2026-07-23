package com.sublingo.app.ui.transcript

import com.sublingo.app.data.vocabulary.VocabularyItemType
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptLayoutStabilityTest {
    @Test fun phraseTokenIdentityDoesNotChangeWhenPlaybackSelectionChanges() {
        val aligned = TranscriptWordAligner.align(
            "They show a video at school.",
            "他们在学校播放一段视频。",
            listOf(TranscriptHighlight("show-video", "show a video", listOf("播放一段视频"), VocabularyItemType.COLLOCATION)),
        )
        val phrase = aligned.english.single { it.alignmentId != null }
        assertEquals("show a video", phrase.text)
        assertEquals(setOf(phrase.alignmentId), phrase.alignmentIds)
    }
}
