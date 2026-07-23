package com.sublingo.app.data.review

import com.sublingo.app.data.db.ReviewCardEntity
import com.sublingo.app.data.db.ReviewLogEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

enum class ReviewRating { AGAIN, GOOD }

data class ReviewSchedule(
    val repetitions: Int,
    val intervalDays: Int,
    val easeFactor: Double,
    val dueAt: Long,
)

object BinarySm2Scheduler {
    private const val DayMs = 24L * 60L * 60L * 1_000L

    fun next(card: ReviewCardEntity, rating: ReviewRating, reviewedAt: Long): ReviewSchedule = when (rating) {
        ReviewRating.AGAIN -> ReviewSchedule(
            repetitions = 0,
            intervalDays = 1,
            easeFactor = max(1.3, card.easeFactor - 0.2),
            dueAt = reviewedAt + DayMs,
        )

        ReviewRating.GOOD -> {
            val interval = when (card.repetitions) {
                0 -> 1
                1 -> 6
                else -> max(1, (card.intervalDays * card.easeFactor).roundToInt())
            }
            ReviewSchedule(
                repetitions = card.repetitions + 1,
                intervalDays = interval,
                easeFactor = (card.easeFactor + 0.1).coerceAtMost(3.0),
                dueAt = reviewedAt + interval * DayMs,
            )
        }
    }
}

data class DailyReviewStats(
    val date: LocalDate,
    val reviews: Int = 0,
    val good: Int = 0,
    val again: Int = 0,
    val newWords: Int = 0,
) {
    val accuracy: Int get() = if (reviews == 0) 0 else ((good * 100f) / reviews).roundToInt()
    val level: Int get() = HeatmapLevels.level(reviews)
}

data class ReviewOverview(
    val todayLearned: Int,
    val dueCount: Int,
    val masteredCount: Int,
    val currentStreak: Int,
    val totalLearningDays: Int,
    val daily: Map<LocalDate, DailyReviewStats>,
)

object HeatmapLevels {
    fun level(count: Int): Int = when (count) {
        0 -> 0
        in 1..9 -> 1
        in 10..19 -> 2
        in 20..39 -> 3
        else -> 4
    }
}

object ReviewStatsAggregator {
    fun daily(logs: List<ReviewLogEntity>, zoneId: ZoneId): Map<LocalDate, DailyReviewStats> =
        logs.groupBy { Instant.ofEpochMilli(it.reviewedAt).atZone(zoneId).toLocalDate() }
            .mapValues { (date, entries) ->
                DailyReviewStats(
                    date = date,
                    reviews = entries.size,
                    good = entries.count { it.rating == ReviewRating.GOOD.name },
                    again = entries.count { it.rating == ReviewRating.AGAIN.name },
                    newWords = entries.map { it.cardId }.toSet().count { cardId ->
                        entries.any { it.cardId == cardId && it.previousRepetitions == 0 }
                    },
                )
            }

    fun currentStreak(activeDates: Set<LocalDate>, today: LocalDate): Int {
        if (activeDates.isEmpty()) return 0
        var cursor = if (today in activeDates) today else today.minusDays(1)
        var streak = 0
        while (cursor in activeDates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun windowDates(today: LocalDate): List<LocalDate> {
        val start = today.minusMonths(12).plusDays(1)
        val days = ChronoUnit.DAYS.between(start, today).toInt()
        return (0..days).map { start.plusDays(it.toLong()) }
    }

    fun heatmapWeeks(today: LocalDate): List<List<LocalDate>> {
        val window = windowDates(today)
        val first = window.first().minusDays((window.first().dayOfWeek.value - 1).toLong())
        val last = today.plusDays((7 - today.dayOfWeek.value).toLong())
        val days = ChronoUnit.DAYS.between(first, last).toInt()
        return (0..days).map { first.plusDays(it.toLong()) }.chunked(7)
    }
}
