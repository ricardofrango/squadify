package com.ricardofrango.clubmanager.domain

import com.ricardofrango.clubmanager.model.SeasonType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The "change type -> reset dates to that type's default" business rule behind the Create Season
 * screen. Worked examples match the design canvas referenced in `AI_GUIDELINES.md` ("UI Design
 * Reference"): for today = 2026-09-04, European year defaults to 1 Jul 2026 - 30 Jun 2027 and
 * Calendar year defaults to 2026 (Jan 1 - Dec 31).
 */
class SeasonDateDefaultsTest {

    @Test
    fun calendarYearDefaultsToJanFirstThroughDecThirtyFirstOfTodaysYear() {
        val today = LocalDate(2026, 9, 4)

        val (start, end) = SeasonDateDefaults.datesFor(SeasonType.CALENDAR_YEAR, today)

        assertEquals(LocalDate(2026, 1, 1), start)
        assertEquals(LocalDate(2026, 12, 31), end)
    }

    @Test
    fun europeanYearDefaultsToTheSeasonCurrentlyRunning_whenTodayIsAfterJulyFirst() {
        val today = LocalDate(2026, 9, 4)

        val (start, end) = SeasonDateDefaults.datesFor(SeasonType.EUROPEAN_YEAR, today)

        assertEquals(LocalDate(2026, 7, 1), start)
        assertEquals(LocalDate(2027, 6, 30), end)
    }

    @Test
    fun europeanYearDefaultsToThePreviousJuly_whenTodayIsBeforeJulyFirst() {
        val today = LocalDate(2026, 3, 15)

        val (start, end) = SeasonDateDefaults.datesFor(SeasonType.EUROPEAN_YEAR, today)

        assertEquals(LocalDate(2025, 7, 1), start)
        assertEquals(LocalDate(2026, 6, 30), end)
    }

    @Test
    fun europeanYearOnJulyFirstItselfCountsAsTheNewSeasonAlreadyStarted() {
        val today = LocalDate(2026, 7, 1)

        val (start, end) = SeasonDateDefaults.datesFor(SeasonType.EUROPEAN_YEAR, today)

        assertEquals(LocalDate(2026, 7, 1), start)
        assertEquals(LocalDate(2027, 6, 30), end)
    }

    @Test
    fun inferredNameForEuropeanYearIsTwoDigitStartSlashTwoDigitEnd() {
        val name = SeasonDateDefaults.inferredName(
            SeasonType.EUROPEAN_YEAR,
            startDate = LocalDate(2026, 7, 1),
            endDate = LocalDate(2027, 6, 30)
        )

        assertEquals("26/27", name)
    }

    @Test
    fun inferredNameForCalendarYearIsTheFullStartYear() {
        val name = SeasonDateDefaults.inferredName(
            SeasonType.CALENDAR_YEAR,
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2026, 12, 31)
        )

        assertEquals("2026", name)
    }
}
