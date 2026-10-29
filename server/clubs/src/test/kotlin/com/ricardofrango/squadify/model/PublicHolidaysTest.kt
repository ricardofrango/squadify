package com.ricardofrango.squadify.model

import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals

// Portugal's national holidays (design 39d's example: 1 Dec, 8 Dec, 25 Dec and 1 Jan between 6 Oct 2026
// and 8 Jan 2027), including the ones that move with Easter.
class PublicHolidaysTest {
    @Test
    fun easterFallsOnTheRightSunday() {
        assertEquals(LocalDate(2026, 4, 5), easterSunday(2026))
        assertEquals(LocalDate(2027, 3, 28), easterSunday(2027))
        assertEquals(LocalDate(2024, 3, 31), easterSunday(2024))
    }

    @Test
    fun portugalHasThirteenNationalHolidaysIncludingTheMovingOnes() {
        val holidays = publicHolidays(Country.PORTUGAL, 2027).associate { it.holiday to it.date }
        assertEquals(13, holidays.size)
        assertEquals(LocalDate(2027, 3, 26), holidays[Holiday.GOOD_FRIDAY])
        assertEquals(LocalDate(2027, 5, 27), holidays[Holiday.CORPUS_CHRISTI])
    }

    @Test
    fun theDesignsPeriodSkipsFourHolidays() {
        val between = publicHolidaysBetween(Country.PORTUGAL, LocalDate(2026, 10, 6), LocalDate(2027, 1, 8))
        assertEquals(
            listOf(LocalDate(2026, 11, 1), LocalDate(2026, 12, 1), LocalDate(2026, 12, 8), LocalDate(2026, 12, 25), LocalDate(2027, 1, 1)),
            between.keys.sorted()
        )
    }

    @Test
    fun anUnknownCountryCodeFallsBackToPortugal() {
        assertEquals(Country.PORTUGAL, Country.fromCode("XX"))
    }
}
