package com.ricardofrango.squadify.domain

import com.ricardofrango.squadify.model.SeasonType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * Default start/end dates and inferred name for a [SeasonType], derived from the convention each
 * enum value documents (`CALENDAR_YEAR`: Jan 1st - Dec 31st; `EUROPEAN_YEAR`: Jul 1st - Jun 30th).
 *
 * Used by the "Create Season" screen: selecting a type fills these defaults into the date fields --
 * unconditionally, even overwriting dates the user already edited, because the type is the primary
 * decision (it determines both the date rule and the inferred name; the dates are a consequence of it,
 * not an independent choice). [today] is passed in explicitly (rather than read from `Clock.System`
 * internally) purely to keep this pure and trivially testable.
 */
object SeasonDateDefaults {

    fun datesFor(type: SeasonType, today: LocalDate): Pair<LocalDate, LocalDate> = when (type) {
        SeasonType.CALENDAR_YEAR ->
            LocalDate(today.year, 1, 1) to LocalDate(today.year, 12, 31)

        SeasonType.EUROPEAN_YEAR -> {
            // The European season currently running: if today is on/after Jul 1st, it started this
            // year and ends next year; otherwise it started last year and ends this year.
            val startYear = if (today.month >= Month.JULY) today.year else today.year - 1
            LocalDate(startYear, 7, 1) to LocalDate(startYear + 1, 6, 30)
        }
    }

    /**
     * Mirrors `CreateSeasonUseCase`'s server-side name inference exactly (same formula, applied to
     * [startDate]/[endDate]) so the client can preview the inferred name before submitting, e.g. as a
     * field placeholder. Keep in sync with the server if that inference ever changes.
     */
    fun inferredName(type: SeasonType, startDate: LocalDate, endDate: LocalDate): String = when (type) {
        SeasonType.EUROPEAN_YEAR -> {
            val start = startDate.year.toString().takeLast(2)
            val end = endDate.year.toString().takeLast(2)
            "$start/$end"
        }

        SeasonType.CALENDAR_YEAR -> startDate.year.toString()
    }
}
