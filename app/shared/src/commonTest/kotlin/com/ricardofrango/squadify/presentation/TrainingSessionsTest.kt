package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.model.TrainingSessions
import com.ricardofrango.squadify.presentation.ui.training.CreateSessionsForm
import com.ricardofrango.squadify.presentation.ui.training.SessionChip
import com.ricardofrango.squadify.presentation.ui.training.SlotForm
import com.ricardofrango.squadify.presentation.ui.training.chipFor
import com.ricardofrango.squadify.presentation.ui.training.sessionCount
import com.ricardofrango.squadify.presentation.ui.training.splitSessions
import com.ricardofrango.squadify.presentation.ui.training.toRequest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

// Training sessions on the device (design 39): the Preview count, and the Training tab's list.
class TrainingSessionsTest {
    private val season = Season("s", "c", "26/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 9, 1), LocalDate(2027, 6, 30), true)
    private val sessions = TrainingSessions(AgeGroup("ag", "c", "Sub-15", "s", 2012, 2012), "CD Ribamar", season, sessions = emptyList(), canEdit = true, canRate = true)
    private val form = CreateSessionsForm(
        slots = listOf(SlotForm(setOf(2), LocalTime(18, 0), LocalTime(19, 0)), SlotForm(setOf(5), LocalTime(18, 30), LocalTime(20, 0))),
        startDate = LocalDate(2026, 10, 6), endDate = LocalDate(2027, 1, 8), location = " Campo do Ribamar "
    )

    @Test
    fun thePreviewButtonCountsTheSessionsWithoutHolidays() {
        assertEquals(24, form.sessionCount(sessions))
        assertEquals(28, form.copy(includeHolidays = true).sessionCount(sessions))
    }

    @Test
    fun anIncompleteFormCountsNothing() {
        assertEquals(0, form.copy(slots = listOf(SlotForm())).sessionCount(sessions))
        assertEquals(0, form.copy(endDate = LocalDate(2027, 7, 31)).sessionCount(sessions))
        assertEquals(0, form.copy(slots = listOf(SlotForm(setOf(2), LocalTime(19, 0), LocalTime(18, 0)))).sessionCount(sessions))
    }

    @Test
    fun theRequestTrimsTheLocationAndSortsWeekdays() {
        val request = form.copy(slots = listOf(SlotForm(setOf(5, 2)))).toRequest()!!
        assertEquals("Campo do Ribamar", request.location)
        assertEquals(listOf(2, 5), request.slots.single().weekdays)
    }

    private fun session(id: String, date: LocalDate, cancelled: Boolean = false, rated: Int? = null) =
        TrainingSession(id, "ag", date, LocalTime(18, 0), LocalTime(19, 0), cancelledAt = if (cancelled) Instant.fromEpochMilliseconds(0) else null, ratedCount = rated)

    @Test
    fun upcomingSoonestFirstAndPastNewestFirst() {
        val now = LocalDateTime(2026, 10, 15, 12, 0)
        val (upcoming, past) = splitSessions(
            listOf(session("a", LocalDate(2026, 10, 8)), session("b", LocalDate(2026, 10, 19)), session("c", LocalDate(2026, 10, 15)), session("d", LocalDate(2026, 10, 1))),
            now
        )
        assertEquals(listOf("c", "b"), upcoming.map { it.id })
        assertEquals(listOf("a", "d"), past.map { it.id })
    }

    @Test
    fun chips() {
        val now = LocalDateTime(2026, 10, 15, 12, 0)
        val today = session("t", LocalDate(2026, 10, 15))
        val next = session("n", LocalDate(2026, 10, 19))
        assertEquals(SessionChip.TODAY, chipFor(today, now, next, 18, canRate = true))
        assertEquals(SessionChip.NEXT, chipFor(next, now, next, 18, canRate = true))
        assertEquals(SessionChip.CANCELLED, chipFor(session("x", LocalDate(2026, 10, 22), cancelled = true), now, next, 18, canRate = true))
        assertEquals(SessionChip.NEEDS_RATINGS, chipFor(session("p", LocalDate(2026, 10, 8), rated = 12), now, next, 18, canRate = true))
        // Fully rated, or someone who can't rate: no chip.
        assertNull(chipFor(session("p", LocalDate(2026, 10, 8), rated = 18), now, next, 18, canRate = true))
        assertNull(chipFor(session("p", LocalDate(2026, 10, 8)), now, next, 18, canRate = false))
    }
}
