package com.sublingo.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sublingo.app.data.db.ReviewStudyCardRow
import com.sublingo.app.data.review.DailyReviewStats
import com.sublingo.app.data.review.ReviewAction
import com.sublingo.app.data.review.ReviewOverview
import com.sublingo.app.data.review.ReviewRating
import com.sublingo.app.data.review.ReviewRepository
import com.sublingo.app.data.review.ReviewStatsAggregator
import com.sublingo.app.data.vocabulary.StandardDictionarySenseRepairer
import com.sublingo.app.data.vocabulary.VocabularyDifficulty
import com.sublingo.app.data.vocabulary.VocabularyDifficultyBackfiller
import com.sublingo.app.data.vocabulary.VocabularyDifficultyPreferences
import com.sublingo.app.data.review.matchesDifficulty
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn

enum class ReviewSection { STUDY, WORDS, STATS }

data class ReviewScope(
    val sourceVideoId: String? = null,
    val label: String = "全部单词",
    val favoritesOnly: Boolean = false,
)

data class ReviewWordBook(val sourceVideoId: String, val title: String, val cardCount: Int)

data class ReviewUiState(
    val section: ReviewSection = ReviewSection.STUDY,
    val cards: List<ReviewStudyCardRow> = emptyList(),
    val reviewScope: ReviewScope = ReviewScope(),
    val wordBooks: List<ReviewWordBook> = emptyList(),
    val allCards: List<ReviewStudyCardRow> = emptyList(),
    val difficulty: VocabularyDifficulty = VocabularyDifficulty.B1,
    val filteredOutCount: Int = 0,
    val allDueCount: Int = 0,
    val session: List<ReviewStudyCardRow> = emptyList(),
    val sessionTotal: Int = 0,
    val completed: Int = 0,
    val current: ReviewStudyCardRow? = null,
    val overview: ReviewOverview = ReviewOverview(0, 0, 0, 0, 0, emptyMap()),
    val selectedDate: LocalDate? = null,
    val selectedDayStats: DailyReviewStats? = null,
    val canUndo: Boolean = false,
    val busy: Boolean = false,
)

private data class ReviewHistory(val action: ReviewAction, val card: ReviewStudyCardRow)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: ReviewRepository,
    private val senseRepairer: StandardDictionarySenseRepairer,
    private val difficultyPreferences: VocabularyDifficultyPreferences,
    private val difficultyBackfiller: VocabularyDifficultyBackfiller,
) : ViewModel() {
    private val section = MutableStateFlow(ReviewSection.STUDY)
    private val reviewScope = MutableStateFlow(ReviewScope())
    private val session = MutableStateFlow<List<ReviewStudyCardRow>>(emptyList())
    private val sessionTotal = MutableStateFlow(0)
    private val history = MutableStateFlow<List<ReviewHistory>>(emptyList())
    private val busy = MutableStateFlow(false)
    private val selectedDate = MutableStateFlow<LocalDate?>(null)
    private val now: Long get() = System.currentTimeMillis()

    private val transientState = combine(
        sessionTotal, history, busy, selectedDate, difficultyPreferences.minimum,
    ) { total, actions, isBusy, date, difficulty -> Quint(total, actions, isBusy, date, difficulty) }
        .combine(reviewScope) { transient, scope -> transient to scope }

    val uiState: StateFlow<ReviewUiState> = combine(
        repository.observeCards(), repository.observeLogs(), section, session, transientState,
    ) { cards, logs, selectedSection, studySession, scopedTransient ->
        val transient = scopedTransient.first
        val scope = scopedTransient.second
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val daily = ReviewStatsAggregator.daily(logs, zone)
        val activeDates = daily.keys
        val filteredCards = cards.filter { it.matchesDifficulty(transient.fifth) }
        val overview = ReviewOverview(
            todayLearned = daily[today]?.reviews ?: 0,
            dueCount = filteredCards.count { it.dueAt <= now },
            masteredCount = filteredCards.count { it.repetitions >= 3 },
            currentStreak = ReviewStatsAggregator.currentStreak(activeDates, today),
            totalLearningDays = activeDates.size,
            daily = daily,
        )
        ReviewUiState(
            section = selectedSection,
            cards = filteredCards,
            reviewScope = scope,
            wordBooks = cards.filter { it.sourceVideoId != null }.groupBy { it.sourceVideoId!! }
                .map { (id, rows) -> ReviewWordBook(id, rows.first().sourceVideoTitle ?: "未命名视频", rows.size) }
                .sortedBy { it.title.lowercase() },
            allCards = cards,
            difficulty = transient.fifth,
            filteredOutCount = cards.size - filteredCards.size,
            allDueCount = cards.count { it.dueAt <= now },
            session = studySession,
            sessionTotal = transient.first,
            completed = (transient.first - studySession.size).coerceAtLeast(0),
            current = studySession.firstOrNull(),
            overview = overview,
            selectedDate = transient.fourth,
            selectedDayStats = transient.fourth?.let { daily[it] ?: DailyReviewStats(it) },
            canUndo = transient.second.isNotEmpty(),
            busy = transient.third,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    init {
        viewModelScope.launch {
            senseRepairer.repairOutdatedSenses()
            difficultyBackfiller.backfill()
            refreshSession()
        }
        viewModelScope.launch {
            difficultyPreferences.minimum.drop(1).collect { refreshSession() }
        }
    }

    fun selectSection(value: ReviewSection) { section.value = value }

    fun selectReviewScope(scope: ReviewScope) {
        reviewScope.value = scope
        refreshSession()
    }

    fun refreshSession() {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            val cards = repository.studyCards(
                minimum = difficultyPreferences.minimum.value,
                sourceVideoId = reviewScope.value.sourceVideoId,
                favoritesOnly = reviewScope.value.favoritesOnly,
                limit = 25,
            )
            session.value = cards
            sessionTotal.value = cards.size
            history.value = emptyList()
            busy.value = false
        }
    }

    fun rate(rating: ReviewRating) {
        val card = session.value.firstOrNull() ?: return
        if (busy.value) return
        busy.value = true
        // The next card is already composed behind this one. Advance the in-memory queue now so a
        // Room transaction and the resulting aggregate projections cannot hold up the visual swap.
        session.value = session.value.drop(1)
        viewModelScope.launch {
            try {
                val action = repository.rate(card.cardId, rating, now)
                if (action != null) {
                    history.value = history.value + ReviewHistory(action, card)
                } else if (session.value.none { it.cardId == card.cardId }) {
                    session.value = listOf(card) + session.value
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (session.value.none { it.cardId == card.cardId }) {
                    session.value = listOf(card) + session.value
                }
            } finally {
                busy.value = false
            }
        }
    }

    fun undo() {
        val last = history.value.lastOrNull() ?: return
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            repository.undo(last.action)
            history.value = history.value.dropLast(1)
            session.value = listOf(last.card) + session.value
            busy.value = false
        }
    }

    fun selectDate(date: LocalDate) { selectedDate.value = date }
    fun clearSelectedDate() { selectedDate.value = null }
    fun setDifficulty(value: VocabularyDifficulty) { difficultyPreferences.setMinimum(value) }

    fun toggleFavorite(card: ReviewStudyCardRow) {
        val favorite = !card.isFavorite
        session.value = if (!favorite && reviewScope.value.favoritesOnly) {
            sessionTotal.value = (sessionTotal.value - 1).coerceAtLeast(0)
            session.value.filterNot { it.cardId == card.cardId }
        } else {
            session.value.map { if (it.cardId == card.cardId) it.copy(isFavorite = favorite) else it }
        }
        viewModelScope.launch { repository.setFavorite(card.cardId, favorite) }
    }

    fun addCard(word: String, phonetic: String?, partOfSpeech: String?, definitionZh: String?) {
        viewModelScope.launch {
            repository.addManualCard(word, phonetic, partOfSpeech, definitionZh)
            if (session.value.isEmpty()) refreshSession()
        }
    }

    fun editCard(card: ReviewStudyCardRow, word: String, phonetic: String?, partOfSpeech: String?, definitionZh: String?) {
        viewModelScope.launch { repository.editCard(card, word, phonetic, partOfSpeech, definitionZh) }
    }

    fun deleteCard(card: ReviewStudyCardRow) {
        viewModelScope.launch {
            repository.deleteCard(card.lexemeId)
            session.value = session.value.filterNot { it.lexemeId == card.lexemeId }
        }
    }

    fun deleteCards(cards: List<ReviewStudyCardRow>) {
        if (cards.isEmpty()) return
        viewModelScope.launch {
            busy.value = true
            cards.distinctBy { it.lexemeId }.forEach { repository.deleteCard(it.lexemeId) }
            val deletedIds = cards.mapTo(mutableSetOf(), ReviewStudyCardRow::lexemeId)
            session.value = session.value.filterNot { it.lexemeId in deletedIds }
            history.value = history.value.filterNot { it.card.lexemeId in deletedIds }
            busy.value = false
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
