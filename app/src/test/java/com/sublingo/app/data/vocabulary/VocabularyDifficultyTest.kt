package com.sublingo.app.data.vocabulary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyDifficultyTest {
    @Test fun levelsHaveStableOrderingAndUnknownRemainsVisible() {
        assertTrue(VocabularyDifficulty.C1.meets(VocabularyDifficulty.B2))
        assertFalse(VocabularyDifficulty.B1.meets(VocabularyDifficulty.B2))
        assertTrue(VocabularyDifficulty.UNKNOWN.meets(VocabularyDifficulty.C1))
    }

    @Test fun commonLocalWordCannotBeOverratedByTechnicalContext() {
        assertEquals(
            VocabularyDifficulty.A1,
            VocabularyDifficultyClassifier.classify("school", "school", VocabularyItemType.WORD, "C1"),
        )
    }

    @Test fun completePhraseUsesContextualDifficultyAndKnownOverride() {
        assertEquals(
            VocabularyDifficulty.B2,
            VocabularyDifficultyClassifier.classify(
                "take it for granted",
                "take it for granted",
                VocabularyItemType.IDIOM,
                "A2",
            ),
        )
        assertEquals(
            VocabularyDifficulty.C1,
            VocabularyDifficultyClassifier.classify(
                "a sophisticated approach",
                "a sophisticated approach",
                VocabularyItemType.COLLOCATION,
                "C1",
            ),
        )
    }

    @Test fun malformedLlmLevelFallsBackLocally() {
        assertEquals(
            VocabularyDifficulty.C1,
            VocabularyDifficultyClassifier.classify("counterintuitive", llmLevel = "advanced"),
        )
    }
}
