package com.sublingo.app.data.review

import androidx.room.withTransaction
import com.sublingo.app.data.db.LexemeEntity
import com.sublingo.app.data.db.LexemeSenseEntity
import com.sublingo.app.data.db.ReviewCardEntity
import com.sublingo.app.data.db.ReviewDao
import com.sublingo.app.data.db.ReviewLogEntity
import com.sublingo.app.data.db.ReviewStudyCardRow
import com.sublingo.app.data.db.SubLingoDatabase
import com.sublingo.app.data.db.VocabularyDao
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import com.sublingo.app.data.vocabulary.VocabularyDifficulty
import com.sublingo.app.data.vocabulary.ContextualChineseMeaningResolver

data class ReviewAction(
    val logId: String,
    val cardBefore: ReviewCardEntity,
    val cardAfter: ReviewCardEntity,
)

@Singleton
class ReviewRepository @Inject constructor(
    private val database: SubLingoDatabase,
    private val reviewDao: ReviewDao,
    private val vocabularyDao: VocabularyDao,
) {
    fun observeCards(): Flow<List<ReviewStudyCardRow>> = combine(
        reviewDao.observeCards(),
        vocabularyDao.observeReviewContextCandidates(),
    ) { cards, contexts -> enhanceContexts(cards, contexts) }
        .flowOn(Dispatchers.Default)
    fun observeLogs(): Flow<List<ReviewLogEntity>> = reviewDao.observeLogs()

    suspend fun studyCards(
        minimum: VocabularyDifficulty,
        sourceVideoId: String? = null,
        favoritesOnly: Boolean = false,
        limit: Int = 25,
    ): List<ReviewStudyCardRow> = enhanceContexts(reviewDao.allCards(), vocabularyDao.reviewContextCandidates())
        .filter { !it.lemma.trim().contains(Regex("\\s")) }
        .filter { it.matchesDifficulty(minimum) }
        .filter { sourceVideoId == null || it.sourceVideoId == sourceVideoId }
        .filter { !favoritesOnly || it.isFavorite }
        .shuffled()
        .take(limit)

    suspend fun setFavorite(cardId: String, isFavorite: Boolean) {
        reviewDao.setFavorite(cardId, isFavorite)
    }

    private fun enhanceContexts(
        cards: List<ReviewStudyCardRow>,
        contexts: List<com.sublingo.app.data.db.ReviewContextCandidateRow>,
    ): List<ReviewStudyCardRow> {
        val byLexeme = contexts.groupBy { it.lexemeId }
        return cards.map { card ->
            val candidates = byLexeme[card.lexemeId].orEmpty()
            val knownTranslations = candidates.mapNotNull { it.translationZh?.trim()?.takeIf(String::isNotEmpty) }.distinct()
            val selected = candidates.map { candidate ->
                val meaning = ContextualChineseMeaningResolver.resolve(
                    contextZh = candidate.contextZh,
                    alignedMeaningZh = candidate.translationZh,
                    definitionZh = card.definitionZh,
                    alignedCandidatesZh = knownTranslations,
                    sourceTerms = listOf(candidate.surfaceForm, card.lemma),
                )
                candidate to meaning
            }.sortedWith(
                compareByDescending<Pair<com.sublingo.app.data.db.ReviewContextCandidateRow, String?>> { it.second != null }
                    .thenBy { it.first.createdAt },
            ).firstOrNull()
            if (selected == null) card else card.copy(
                contextEn = selected.first.contextEn,
                contextZh = selected.first.contextZh,
                sourceSurfaceForm = selected.first.surfaceForm,
                contextualMeaningZh = selected.second,
                sourceVideoId = selected.first.sourceVideoId,
                sourceVideoTitle = selected.first.sourceVideoTitle,
                sourceStartMs = selected.first.sourceStartMs,
            )
        }
    }

    suspend fun rate(cardId: String, rating: ReviewRating, reviewedAt: Long): ReviewAction? = database.withTransaction {
        val current = reviewDao.card(cardId) ?: return@withTransaction null
        val next = BinarySm2Scheduler.next(current, rating, reviewedAt)
        val updated = current.copy(
            repetitions = next.repetitions,
            intervalDays = next.intervalDays,
            easeFactor = next.easeFactor,
            dueAt = next.dueAt,
            lastReviewedAt = reviewedAt,
        )
        val logId = UUID.randomUUID().toString()
        reviewDao.updateCard(updated)
        reviewDao.insertLog(
            ReviewLogEntity(
                id = logId,
                cardId = cardId,
                rating = rating.name,
                reviewedAt = reviewedAt,
                previousRepetitions = current.repetitions,
                previousIntervalDays = current.intervalDays,
                previousEaseFactor = current.easeFactor,
                previousDueAt = current.dueAt,
                nextRepetitions = updated.repetitions,
                nextIntervalDays = updated.intervalDays,
                nextEaseFactor = updated.easeFactor,
                nextDueAt = updated.dueAt,
            ),
        )
        ReviewAction(logId, current, updated)
    }

    suspend fun undo(action: ReviewAction) = database.withTransaction {
        val current = reviewDao.card(action.cardBefore.id)
        if (current != null && current.lastReviewedAt == action.cardAfter.lastReviewedAt) {
            reviewDao.updateCard(action.cardBefore)
            reviewDao.deleteLog(action.logId)
        }
    }

    suspend fun addManualCard(word: String, phonetic: String?, partOfSpeech: String?, definitionZh: String?) {
        val lemma = word.trim()
        require(lemma.isNotEmpty())
        val normalized = lemma.lowercase()
        database.withTransaction {
            val existing = vocabularyDao.findLexeme("en", normalized)
            val lexemeId = existing?.id ?: "lexeme-${UUID.randomUUID()}"
            vocabularyDao.upsertLexeme(
                (existing ?: LexemeEntity(lexemeId, lemma, normalized)).copy(
                    lemma = lemma,
                    normalizedLemma = normalized,
                    phonetic = phonetic?.trim()?.ifBlank { null },
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            vocabularyDao.upsertSenses(
                listOf(
                    LexemeSenseEntity(
                        id = "sense-manual-$lexemeId",
                        lexemeId = lexemeId,
                        pos = partOfSpeech?.trim()?.ifBlank { null },
                        definitionEn = definitionZh?.trim()?.ifBlank { lemma } ?: lemma,
                        definitionZh = definitionZh?.trim()?.ifBlank { null },
                        source = "USER",
                    ),
                ),
            )
            if (vocabularyDao.reviewCardCount(lexemeId) == 0) {
                vocabularyDao.upsertReviewCard(ReviewCardEntity("card-$lexemeId", lexemeId))
            }
        }
    }

    suspend fun editCard(card: ReviewStudyCardRow, word: String, phonetic: String?, partOfSpeech: String?, definitionZh: String?) {
        val lemma = word.trim()
        require(lemma.isNotEmpty())
        database.withTransaction {
            val normalized = lemma.lowercase()
            val collision = vocabularyDao.findLexeme("en", normalized)
            if (collision != null && collision.id != card.lexemeId) return@withTransaction
            vocabularyDao.updateLexeme(
                lexemeId = card.lexemeId,
                lemma = lemma,
                normalizedLemma = normalized,
                phonetic = phonetic?.trim()?.ifBlank { null },
            )
            vocabularyDao.upsertSenses(
                listOf(
                    LexemeSenseEntity(
                        id = card.senseId ?: "sense-manual-${card.lexemeId}",
                        lexemeId = card.lexemeId,
                        pos = partOfSpeech?.trim()?.ifBlank { null },
                        definitionEn = card.definitionEn ?: lemma,
                        definitionZh = definitionZh?.trim()?.ifBlank { null },
                        source = "USER",
                    ),
                ),
            )
        }
    }

    suspend fun deleteCard(lexemeId: String) = vocabularyDao.deleteLexeme(lexemeId)
}

fun ReviewStudyCardRow.matchesDifficulty(minimum: VocabularyDifficulty): Boolean =
    difficultyLevel == null || VocabularyDifficulty.parse(difficultyLevel).meets(minimum)
