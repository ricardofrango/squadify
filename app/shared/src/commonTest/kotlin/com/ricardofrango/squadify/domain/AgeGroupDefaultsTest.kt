package com.ricardofrango.squadify.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AgeGroupDefaultsTest {

    @Test
    fun birthYearOptionsSpanAgesFourThroughNineteen_newestFirst() {
        val today = LocalDate(2026, 9, 4)

        val years = AgeGroupDefaults.birthYearOptions(today)

        assertEquals((2022 downTo 2007).toList(), years)
    }

    @Test
    fun inferredNameIsSubAgeReachedWithinTheSeason() {
        // Age = season END year - birth year: a 2015-born player in a season ending 2027 is "Sub 12".
        assertEquals("Sub 12", AgeGroupDefaults.inferredName(birthYear = 2015, seasonEndYear = 2027))
        assertEquals("Sub 13", AgeGroupDefaults.inferredName(birthYear = 2014, seasonEndYear = 2027))
    }
}
