package com.ricardofrango.squadify.presentation.ui.fullstats

import com.ricardofrango.squadify.model.FullStatsColumn
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FullStatsPeriodTest {

    // A Saturday.
    private val today = LocalDate(2026, 10, 24)

    @Test
    fun eachPresetCoversItsDaysUpToToday() {
        assertEquals(LocalDate(2026, 10, 19) to today, StatsPeriod.THIS_WEEK.range(today, null, null)) // from Monday
        assertEquals(LocalDate(2026, 10, 18) to today, StatsPeriod.LAST_7_DAYS.range(today, null, null)) // today counts
        assertEquals(LocalDate(2026, 10, 1) to today, StatsPeriod.THIS_MONTH.range(today, null, null))
        assertEquals(null to null, StatsPeriod.SEASON.range(today, null, null))
        val friday = LocalDate(2026, 10, 16)
        assertEquals(friday to today, StatsPeriod.CUSTOM.range(today, friday, today))
        // On a Monday, the week is just today.
        val monday = LocalDate(2026, 10, 19)
        assertEquals(monday to monday, StatsPeriod.THIS_WEEK.range(monday, null, null))
    }

    @Test
    fun hiddenColumnsSurviveARoundTripAndUnknownNamesAreIgnored() {
        val hidden = setOf(FullStatsColumn.RED_CARDS, FullStatsColumn.GOALS)
        assertEquals("GOALS,RED_CARDS", hidden.toStored())
        assertEquals(hidden, parseHiddenColumns(hidden.toStored()))
        assertEquals(setOf(FullStatsColumn.ASSISTS), parseHiddenColumns("ASSISTS,PASSES,"))
        assertEquals(emptySet(), parseHiddenColumns(null))
    }
}
