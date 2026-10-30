package com.ricardofrango.squadify.coach.presentation.ui.spacesetup

import com.ricardofrango.squadify.domain.AgeGroupDefaults
import com.ricardofrango.squadify.model.AgeGroupItemRequest
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.presentation.ui.createagegroups.AgeGroupSpanType

/**
 * The first setup's fields (REQUIREMENTS §5c): the space's name and sport, its first age group (birth
 * year or years, name, team size) and its first team. No season: the server picks the one running today.
 * A blank age group name is "Sub N" from the birth year, and a blank team name is the age group's.
 */
data class SpaceSetupForm(
    val name: String = "",
    val sport: SportType = SportType.FOOTBALL,
    val spanType: AgeGroupSpanType = AgeGroupSpanType.SINGLE_YEAR,
    // The younger (more recent) birth year and the older one, as on Create age groups; a single year
    // sets both.
    val startYear: Int? = null,
    val endYear: Int? = null,
    val ageGroupName: String = "",
    val teamSize: Int = SportType.FOOTBALL.teamSizes.last(),
    val teamName: String = ""
) {
    /** The age group's name if left blank, once a birth year is picked. */
    fun inferredAgeGroupName(seasonEndYear: Int): String? = endYear?.let { AgeGroupDefaults.inferredName(it, seasonEndYear) }

    /** The team's name if left blank: the age group's. */
    fun defaultTeamName(seasonEndYear: Int): String? = ageGroupName.trim().ifEmpty { null } ?: inferredAgeGroupName(seasonEndYear)

    val isComplete: Boolean
        get() = name.isNotBlank() && endYear != null && startYear != null && startYear >= endYear

    /** Another sport keeps the team size if it plays it, else takes the sport's largest. */
    fun withSport(sport: SportType): SpaceSetupForm =
        copy(sport = sport, teamSize = teamSize.takeIf { it in sport.teamSizes } ?: sport.teamSizes.last())

    fun withSpanType(spanType: AgeGroupSpanType): SpaceSetupForm = when (spanType) {
        AgeGroupSpanType.SINGLE_YEAR -> copy(spanType = spanType, startYear = endYear)
        AgeGroupSpanType.TWO_YEAR -> copy(spanType = spanType)
    }

    /** The older birth year; a single-year age group is that year alone. */
    fun withEndYear(year: Int): SpaceSetupForm =
        copy(endYear = year, startYear = if (spanType == AgeGroupSpanType.SINGLE_YEAR) year else startYear)

    fun withStartYear(year: Int): SpaceSetupForm = copy(startYear = year)

    /** The request, or null while the form isn't complete. */
    fun toRequest(seasonEndYear: Int): SetUpSpaceRequest? {
        if (!isComplete) return null
        val ageGroupName = ageGroupName.trim().ifEmpty { inferredAgeGroupName(seasonEndYear)!! }
        return SetUpSpaceRequest(
            name = name.trim(),
            sport = sport,
            ageGroup = AgeGroupItemRequest(name = ageGroupName, startYear = startYear!!, endYear = endYear!!, teamSize = teamSize),
            teamName = teamName.trim().ifEmpty { ageGroupName }
        )
    }
}

/** The season the server picks for [seasonEndYear], named as it names it: "26/27". */
fun seasonName(seasonEndYear: Int): String =
    "${(seasonEndYear - 1).toString().takeLast(2)}/${seasonEndYear.toString().takeLast(2)}"
