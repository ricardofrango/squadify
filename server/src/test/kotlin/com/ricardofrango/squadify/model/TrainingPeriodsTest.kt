package com.ricardofrango.squadify.model

import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The periods of training plans (design 40i): months of the season, Monday–Sunday weeks, and a week
// that crosses two months belongs to the month of its Monday.
class TrainingPeriodsTest {
    private val season = Season("s", "c", "26/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 9, 1), LocalDate(2027, 6, 30), true)

    @Test
    fun monthsAreEveryCalendarMonthTheSeasonTouches() {
        val months = season.monthStarts()
        assertEquals(10, months.size)
        assertEquals(LocalDate(2026, 9, 1), months.first())
        assertEquals(LocalDate(2027, 6, 1), months.last())
    }

    @Test
    fun weeksStartOnMondayAndIncludeTheOneTheSeasonStartsIn() {
        val weeks = season.weekStarts()
        // 1 Sep 2026 is a Tuesday.
        assertEquals(LocalDate(2026, 8, 31), weeks.first())
        assertEquals(LocalDate(2027, 6, 28), weeks.last())
        assertEquals(mondayOf(LocalDate(2026, 10, 18)), LocalDate(2026, 10, 12))
    }

    @Test
    fun aWeekCrossingMonthsBelongsToTheMonthOfItsMonday() {
        // 28 Sep – 4 Oct is September's; 26 Oct – 1 Nov is October's.
        val october = weeksOfMonth(season, LocalDate(2026, 10, 1))
        assertEquals(listOf(5, 12, 19, 26), october.map { it.day })
        assertTrue(LocalDate(2026, 9, 28) in weeksOfMonth(season, LocalDate(2026, 9, 1)))
    }

    @Test
    fun onlyRealPeriodStartsAreAccepted() {
        assertTrue(season.isPeriodStart(TrainingPlanLevel.SEASON, LocalDate(2026, 9, 1)))
        assertTrue(season.isPeriodStart(TrainingPlanLevel.MONTH, LocalDate(2026, 10, 1)))
        assertFalse(season.isPeriodStart(TrainingPlanLevel.MONTH, LocalDate(2026, 10, 2)))
        assertFalse(season.isPeriodStart(TrainingPlanLevel.MONTH, LocalDate(2027, 7, 1)))
        assertTrue(season.isPeriodStart(TrainingPlanLevel.WEEK, LocalDate(2026, 10, 12)))
        assertFalse(season.isPeriodStart(TrainingPlanLevel.WEEK, LocalDate(2026, 10, 13)))
    }

    @Test
    fun periodEnds() {
        assertEquals(LocalDate(2026, 10, 31), periodEnd(TrainingPlanLevel.MONTH, LocalDate(2026, 10, 1), season))
        assertEquals(LocalDate(2026, 10, 18), periodEnd(TrainingPlanLevel.WEEK, LocalDate(2026, 10, 12), season))
    }
}
