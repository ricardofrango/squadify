package com.ricardofrango.footballmanager.domain

import kotlinx.datetime.LocalDate

/**
 * Selectable birth years and inferred naming for the "Create Age Groups" screen. `CreateAgeGroupsUseCase`
 * (server) requires a non-blank name for every item and doesn't infer one itself (unlike
 * `CreateSeasonUseCase`), so the client computes the same "fills itself" behavior the design calls for
 * and sends the result -- the inferred name is never actually blank on the wire.
 */
object AgeGroupDefaults {

    /**
     * Birth years for ages 4 through 19 relative to [today], newest first -- covers the youth range a
     * club's age groups realistically span, without hardcoding a wider list nobody would pick from.
     */
    fun birthYearOptions(today: LocalDate): List<Int> = (today.year - 4 downTo today.year - 19).toList()

    /**
     * Mirrors the "Sub N" naming already used by real age-group data in this club (e.g. a 2015-born
     * player in a season starting 2026 is "Sub 11"): age = the season's start year minus the birth year.
     */
    fun inferredName(birthYear: Int, seasonStartYear: Int): String {
        val age = seasonStartYear - birthYear
        return "Sub $age"
    }
}
