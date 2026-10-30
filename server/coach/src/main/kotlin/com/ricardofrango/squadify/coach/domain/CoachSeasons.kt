package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * A coach's seasons are automatic (TECHNICAL_DECISIONS §72): the one running on a date, July to June,
 * is picked, or created the first time it's needed. Named like the clubs' European seasons, "26/27".
 */
class CoachSeasons(
    private val seasonRepository: SeasonRepository,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.UTC) }
) {
    /** The space's season running today. Call inside a transaction. */
    suspend fun current(clubId: String): Season {
        val (start, end) = datesAround(today())
        return seasonRepository.findByDetails(clubId, SeasonType.EUROPEAN_YEAR, start, end)
            ?: seasonRepository.createSeason(
                Season(
                    id = Uuid.random().toString(),
                    clubId = clubId,
                    name = "${start.year.toString().takeLast(2)}/${end.year.toString().takeLast(2)}",
                    type = SeasonType.EUROPEAN_YEAR,
                    startDate = start,
                    endDate = end,
                    active = true
                )
            )
    }

    companion object {
        /** July 1st to June 30th around [date]. */
        fun datesAround(date: LocalDate): Pair<LocalDate, LocalDate> {
            val startYear = if (date.month >= Month.JULY) date.year else date.year - 1
            return LocalDate(startYear, 7, 1) to LocalDate(startYear + 1, 6, 30)
        }
    }
}
