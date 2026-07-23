package com.sublingo.app.data.review

import com.sublingo.app.data.db.ReviewStudyCardRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewFavoritesTest {
    @Test fun favoriteFlagCanBeToggledWithoutChangingReviewProgress() {
        val original = card(isFavorite = false)
        val favorite = original.copy(isFavorite = true)

        assertFalse(original.isFavorite)
        assertTrue(favorite.isFavorite)
        assertEquals(original.repetitions, favorite.repetitions)
        assertEquals(original.dueAt, favorite.dueAt)
    }

    private fun card(isFavorite: Boolean) = ReviewStudyCardRow(
        cardId = "card", lexemeId = "lexeme", isFavorite = isFavorite,
        repetitions = 2, intervalDays = 3, easeFactor = 2.5, dueAt = 1234,
        lastReviewedAt = null, createdAt = 0, lemma = "context", normalizedLemma = "context",
        phonetic = null, audioUrl = null, senseId = null, partOfSpeech = null,
        definitionEn = null, definitionZh = null, contextEn = null, contextZh = null,
        sourceSurfaceForm = null, contextualMeaningZh = null, sourceVideoId = null,
        sourceVideoTitle = null, sourceStartMs = null, difficultyLevel = "B1",
    )
}
