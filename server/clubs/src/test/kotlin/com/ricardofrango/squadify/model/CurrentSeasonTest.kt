package com.ricardofrango.squadify.model

import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which season Home's to-dos and the viewer's age groups are scoped to (Season.currentSeason). */
class CurrentSeasonTest {
    private fun season(name: String, start: String, end: String, active: Boolean) =
        Season(name, "club", name, SeasonType.EUROPEAN_YEAR, LocalDate.parse(start), LocalDate.parse(end), active)

    private val old = season("25/26", "2025-07-01", "2026-06-30", active = true)
    private val now = season("26/27", "2026-07-01", "2027-06-30", active = true)
    private val today = LocalDate.parse("2026-11-06")

    @Test
    fun withTwoActiveSeasonsTheOneTodayFallsInWinsInAnyOrder() {
        // Staging: 25/26 left active beside 26/27, and Home landed on 25/26 with no to-dos.
        assertEquals("26/27", listOf(old, now).currentSeason(today)?.name)
        assertEquals("26/27", listOf(now, old).currentSeason(today)?.name)
    }

    @Test
    fun theActiveFlagDoesntCount() {
        assertEquals("26/27", listOf(old, now.copy(active = false)).currentSeason(today)?.name)
        assertEquals("26/27", listOf(old.copy(active = false), now.copy(active = false)).currentSeason(today)?.name)
    }

    @Test
    fun betweenSeasonsTheOneThatEndedLastUntilTheNextStarts() {
        val gap = listOf(season("25/26", "2025-07-01", "2026-05-31", true), season("26/27", "2026-07-01", "2027-06-30", true))
        assertEquals("25/26", gap.currentSeason(LocalDate.parse("2026-06-15"))?.name)
        assertEquals("26/27", gap.currentSeason(LocalDate.parse("2026-07-01"))?.name)
    }

    @Test
    fun onlyFutureSeasonsGiveTheNextToStartAndNoneGivesNull() {
        assertEquals("26/27", listOf(now, season("27/28", "2027-07-01", "2028-06-30", true)).currentSeason(LocalDate.parse("2026-01-10"))?.name)
        assertNull(emptyList<Season>().currentSeason(today))
    }
}
