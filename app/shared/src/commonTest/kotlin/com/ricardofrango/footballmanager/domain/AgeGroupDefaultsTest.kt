package com.ricardofrango.footballmanager.domain

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
    fun inferredNameIsSubAgeAtSeasonStart() {
        // Matches the real data this convention was reverse-engineered from: a 2015-born player in a
        // season starting 2026 is "Sub 11".
        assertEquals("Sub 11", AgeGroupDefaults.inferredName(birthYear = 2015, seasonStartYear = 2026))
        assertEquals("Sub 12", AgeGroupDefaults.inferredName(birthYear = 2014, seasonStartYear = 2026))
    }
}
