package com.sublingo.app.data.review

import com.sublingo.app.data.db.ReviewCardEntity
import com.sublingo.app.data.db.ReviewLogEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewLearningTest {
    @Test fun againResetsAndGoodExpandsStableIntervals() {
        val now = 1_700_000_000_000L
        val newCard = ReviewCardEntity("c", "l", dueAt = now)
        val firstGood = BinarySm2Scheduler.next(newCard, ReviewRating.GOOD, now)
        assertEquals(1, firstGood.repetitions)
        assertEquals(1, firstGood.intervalDays)

        val secondGood = BinarySm2Scheduler.next(
            newCard.copy(repetitions = firstGood.repetitions, intervalDays = firstGood.intervalDays, easeFactor = firstGood.easeFactor),
            ReviewRating.GOOD,
            now,
        )
        assertEquals(6, secondGood.intervalDays)

        val again = BinarySm2Scheduler.next(newCard.copy(repetitions = 4, intervalDays = 30), ReviewRating.AGAIN, now)
        assertEquals(0, again.repetitions)
        assertEquals(1, again.intervalDays)
        assertTrue(again.easeFactor < newCard.easeFactor)
    }

    @Test fun heatmapThresholdsMatchAcceptanceDefinition() {
        assertEquals(listOf(0, 1, 1, 2, 3, 4), listOf(0, 1, 9, 10, 20, 40).map(HeatmapLevels::level))
    }

    @Test fun localDayAggregationAndStreakCrossYearBoundary() {
        val zone = ZoneId.of("Asia/Shanghai")
        val dec31 = LocalDate.of(2025, 12, 31).atTime(23, 50).atZone(zone).toInstant().toEpochMilli()
        val jan1 = LocalDate.of(2026, 1, 1).atTime(0, 10).atZone(zone).toInstant().toEpochMilli()
        val logs = listOf(log("1", dec31, ReviewRating.GOOD), log("2", jan1, ReviewRating.AGAIN))
        val daily = ReviewStatsAggregator.daily(logs, zone)
        assertEquals(2, daily.size)
        assertEquals(2, ReviewStatsAggregator.currentStreak(daily.keys, LocalDate.of(2026, 1, 1)))
        assertEquals(0, daily.getValue(LocalDate.of(2026, 1, 1)).accuracy)
    }

    @Test fun twelveMonthWindowIncludesLeapDayWhenApplicable() {
        val dates = ReviewStatsAggregator.windowDates(LocalDate.of(2024, 3, 1))
        assertTrue(LocalDate.of(2024, 2, 29) in dates)
        assertEquals(LocalDate.of(2023, 3, 2), dates.first())
        assertEquals(LocalDate.of(2024, 3, 1), dates.last())
    }

    @Test fun heatmapUsesCompleteMondayToSundayWeeksAcrossYearBoundary() {
        val weeks = ReviewStatsAggregator.heatmapWeeks(LocalDate.of(2026, 1, 2))
        assertTrue(weeks.all { it.size == 7 })
        assertEquals(1, weeks.first().first().dayOfWeek.value)
        assertEquals(7, weeks.last().last().dayOfWeek.value)
        assertTrue(weeks.flatten().any { it.year == 2025 })
        assertTrue(weeks.flatten().any { it.year == 2026 })
    }

    private fun log(id: String, time: Long, rating: ReviewRating) = ReviewLogEntity(
        id, "card-$id", rating.name, time, 0, 0, 2.5, time, 1, 1, 2.6, time + 86_400_000L,
    )
}
