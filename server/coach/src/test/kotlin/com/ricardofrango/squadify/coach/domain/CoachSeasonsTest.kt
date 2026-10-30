package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals

// A coach's seasons are automatic: July to June, picked from the date, created the first time.
class CoachSeasonsTest {
    private val seasons = mockk<SeasonRepository>()

    @Test
    fun theSeasonRunsFromJulyToJune() {
        assertEquals(LocalDate(2025, 7, 1) to LocalDate(2026, 6, 30), CoachSeasons.datesAround(LocalDate(2026, 6, 30)))
        assertEquals(LocalDate(2026, 7, 1) to LocalDate(2027, 6, 30), CoachSeasons.datesAround(LocalDate(2026, 7, 1)))
        assertEquals(LocalDate(2026, 7, 1) to LocalDate(2027, 6, 30), CoachSeasons.datesAround(LocalDate(2026, 10, 30)))
    }

    @Test
    fun theFirstTimeTheSeasonIsCreatedAndNamed() = runBlocking {
        coEvery { seasons.findByDetails(any(), any(), any(), any()) } returns null
        val created = slot<Season>()
        coEvery { seasons.createSeason(capture(created)) } answers { created.captured }

        val season = CoachSeasons(seasons) { LocalDate(2026, 10, 30) }.current("space-1")

        assertEquals("26/27", season.name)
        assertEquals(SeasonType.EUROPEAN_YEAR, season.type)
        assertEquals("space-1", season.clubId)
    }

    @Test
    fun afterThatTheSameSeasonIsPicked() = runBlocking {
        val existing = Season("s1", "space-1", "26/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 7, 1), LocalDate(2027, 6, 30), true)
        coEvery { seasons.findByDetails("space-1", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 7, 1), LocalDate(2027, 6, 30)) } returns existing

        assertEquals(existing, CoachSeasons(seasons) { LocalDate(2027, 2, 1) }.current("space-1"))
        coVerify(exactly = 0) { seasons.createSeason(any()) }
    }
}
