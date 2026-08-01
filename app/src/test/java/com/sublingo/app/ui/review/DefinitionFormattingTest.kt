package com.sublingo.app.ui.review

import com.sublingo.app.data.db.ReviewStudyCardRow
import org.junit.Assert.assertEquals
import org.junit.Test

class DefinitionFormattingTest {
    @Test fun semicolonSeparatedSensesRenderOnePerLine() {
        assertEquals("n. 工作\nvi. 运转\nvt. 使用", formatDefinitionForReading("n. 工作；vi. 运转; vt. 使用"))
    }

    @Test fun reviewDefinitionNeverFallsBackToEnglish() {
        val card = ReviewStudyCardRow(
            cardId = "card", lexemeId = "lexeme", isFavorite = false, repetitions = 0,
            intervalDays = 0, easeFactor = 2.5, dueAt = 0, lastReviewedAt = null, createdAt = 0,
            lemma = "collectively", normalizedLemma = "collectively", phonetic = null, audioUrl = null,
            senseId = "sense", partOfSpeech = "adverb", definitionEn = "In a collective manner",
            definitionZh = null, contextEn = null, contextZh = null, sourceSurfaceForm = null,
            contextualMeaningZh = null, sourceVideoId = null, sourceVideoTitle = null, sourceStartMs = null,
            difficultyLevel = "B1",
        )

        assertEquals("释义待补全", reviewDefinitionZh(card))
        assertEquals("共同地", reviewDefinitionZh(card.copy(contextualMeaningZh = "共同地")))
    }
}
