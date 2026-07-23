package com.sublingo.app.data.review

import com.sublingo.app.data.db.ReviewStudyCardRow
import com.sublingo.app.data.vocabulary.VocabularyDifficulty
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewDifficultyFilterTest {
    @Test fun automaticCardRespectsMinimumWhileManualCardAlwaysRemainsVisible() {
        assertFalse(card("A2").matchesDifficulty(VocabularyDifficulty.B1))
        assertTrue(card("B2").matchesDifficulty(VocabularyDifficulty.B1))
        assertTrue(card(null).matchesDifficulty(VocabularyDifficulty.C1))
    }

    private fun card(level: String?) = ReviewStudyCardRow(
        cardId = "c", lexemeId = "l", repetitions = 0, intervalDays = 0, easeFactor = 2.5,
        dueAt = 0, lastReviewedAt = null, createdAt = 0, lemma = "word", normalizedLemma = "word",
        phonetic = null, audioUrl = null, senseId = null, partOfSpeech = null, definitionEn = null,
        definitionZh = null, contextEn = null, contextZh = null, sourceSurfaceForm = null, contextualMeaningZh = null,
        sourceVideoId = null, sourceVideoTitle = null, sourceStartMs = null, difficultyLevel = level,
    )
}
