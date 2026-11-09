package com.ricardofrango.squadify.coach.presentation.ui.agegroups

import com.ricardofrango.squadify.model.AgeGroup
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

/** The birth years an age group can have: Sub 5 to Sub 19 of the season. */
fun wizardYearRange(seasonEndYear: Int): IntRange = (seasonEndYear - 19)..(seasonEndYear - 5)

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

/**
 * One age group being filled in, one box each: the birth year as typed ([yearText]), the a-side size (none
 * until picked), in futsal whether it joins the next younger year, and how many teams.
 */
data class AgeGroupRow(
    val key: Int,
    val yearText: String = "",
    val teamSize: Int? = null,
    val joinYounger: Boolean = false,
    val teams: Int = 1
) {
    /** The oldest birth year, once four digits are typed. */
    val oldestYear: Int? get() = yearText.takeIf { it.length == 4 }?.toIntOrNull()

    val startYear: Int? get() = oldestYear?.let { if (joinYounger) it + 1 else it }
}

/** What's wrong with a box, shown under it. */
enum class RowProblem { YEAR_MISSING, YEAR_OUT_OF_RANGE, YEAR_REPEATED, YEAR_TAKEN, SIZE_MISSING }

/** The teams' names: "A", "B"… */
fun teamNames(count: Int): List<String> = (0 until count).map { ('A' + it).toString() }

/** The most teams an age group gets from the wizard (the server's AddAgeGroupsUseCase.MAX_TEAMS). */
const val MAX_WIZARD_TEAMS = 5
