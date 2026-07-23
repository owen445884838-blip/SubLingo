package com.sublingo.app.data.vocabulary

import com.sublingo.app.data.db.VocabularyDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VocabularyDifficultyBackfiller @Inject constructor(
    private val vocabularyDao: VocabularyDao,
) {
    suspend fun backfill(): Int {
        var updated = 0
        vocabularyDao.occurrencesNeedingDifficulty().forEach { row ->
            val type = runCatching { VocabularyItemType.valueOf(row.itemType) }.getOrDefault(VocabularyItemType.WORD)
            val level = VocabularyDifficultyClassifier.classify(row.surfaceForm, row.lemma, type)
            vocabularyDao.updateOccurrenceDifficulty(row.occurrenceId, level.name)
            updated++
        }
        return updated
    }
}
