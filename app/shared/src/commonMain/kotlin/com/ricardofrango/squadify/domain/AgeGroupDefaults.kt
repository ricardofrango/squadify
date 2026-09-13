package com.ricardofrango.squadify.domain

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
     * "Sub N" naming: N = the age a player of [birthYear] reaches within the season, i.e. the season's
     * **end** year minus the birth year. A 2015-born player in a season ending 2027 is "Sub 12".
     */
    fun inferredName(birthYear: Int, seasonEndYear: Int): String {
        val age = seasonEndYear - birthYear
        return "Sub $age"
    }

    /**
     * The end year of the season currently under way, as of [today] -- youth football seasons run
     * autumn-to-spring, so from July onward "this season" is the one ending *next* calendar year.
     * Used to derive a player's "Sub N" label from their birth year alone (no season/age-group lookup
     * needed) wherever a screen shows it purely as a read-only label -- see the player profile screen.
     */
    fun currentSeasonEndYear(today: LocalDate): Int = if (today.monthNumber >= 7) today.year + 1 else today.year
}
