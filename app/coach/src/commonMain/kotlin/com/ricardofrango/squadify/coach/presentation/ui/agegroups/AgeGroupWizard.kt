package com.ricardofrango.squadify.coach.presentation.ui.agegroups

import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.ageGroupClubName
import com.ricardofrango.squadify.model.birthYearsOverlap
import kotlinx.datetime.LocalDate

// The age-group wizard's rules (design "Assistente: Adicionar escalões"), apart from its screen.

/**
 * The year the season running on [today] ends: the coach server's seasons run July 1st to June 30th
 * (CoachSeasons), so 26/27 ends in 2027 from July 2026 to June 2027.
 */
fun seasonEndYear(today: LocalDate): Int = if (today.month.ordinal >= 6) today.year + 1 else today.year

/** "Sub 11": the age reached in the season's last year by the oldest born in [oldestYear]. */
fun subName(seasonEndYear: Int, oldestYear: Int): String = "Sub ${seasonEndYear - oldestYear}"

/** The birth years the wizard offers, youngest first: Sub 5 to Sub 19 of the season. */
fun wizardYears(seasonEndYear: Int): List<Int> = ((seasonEndYear - 5) downTo (seasonEndYear - 19)).toList()

/**
 * The a-side size an age group usually plays at its age, of the ones its sport has: futsal 5; football 5
 * to Sub 7, 7 to Sub 11, 9 to Sub 13, 11 after.
 */
fun defaultTeamSize(sport: SportType, sub: Int): Int {
    val usual = when (sport) {
        SportType.FUTSAL -> 5
        SportType.FOOTBALL -> when {
            sub <= 7 -> 5
            sub <= 11 -> 7
            sub <= 13 -> 9
            else -> 11
        }
    }
    return usual.takeIf { it in sport.teamSizes } ?: sport.teamSizes.last()
}

/**
 * Whether [clubName] already has an age group this season ([seasonId]) sharing a year with
 * [startYear]..[oldestYear]: the server refuses it. Another club's may share them.
 */
fun clubHasYears(existing: List<AgeGroup>, seasonId: String?, clubName: String, startYear: Int, oldestYear: Int): Boolean =
    existing.any {
        it.seasonId == seasonId &&
            ageGroupClubName(it.name)?.lowercase() == clubName.trim().lowercase() &&
            birthYearsOverlap(startYear, oldestYear, it.startYear, it.endYear)
    }

/** One age group picked in the wizard: its birth years (two in futsal when [joinYounger]), a-side size and teams. */
data class PickedAgeGroup(
    val oldestYear: Int,
    val teamSize: Int,
    val joinYounger: Boolean = false,
    val teams: Int = 1
) {
    val startYear: Int get() = if (joinYounger) oldestYear + 1 else oldestYear
}

/** The teams' names: "A", "B"… */
fun teamNames(count: Int): List<String> = (0 until count).map { ('A' + it).toString() }

/** The most teams an age group gets from the wizard (the server's AddAgeGroupsUseCase.MAX_TEAMS). */
const val MAX_WIZARD_TEAMS = 5
