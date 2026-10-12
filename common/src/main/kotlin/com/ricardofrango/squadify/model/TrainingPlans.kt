package com.ricardofrango.squadify.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlin.time.Instant

// Training plans (design 40): an age group's season plan (macrocycle), one plan per calendar month of
// the season (mesocycle) and one per Monday–Sunday week (microcycle). Every level has the same shape --
// focus, objectives, notes -- with phases and key dates on the season and intensity on the week.

@Serializable
enum class TrainingPlanLevel { SEASON, MONTH, WEEK }

@Serializable
enum class TrainingIntensity { LOW, MEDIUM, HIGH }

/** A stretch of the season with its own focus, e.g. "1st half · 28 Sep – 20 Dec". */
@Serializable
data class TrainingPhase(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val focus: String? = null
)

/**
 * One plan. [periodStart] says which one: the season's start date (SEASON), the 1st of the month
 * (MONTH) or the week's Monday (WEEK). [focus] is required for months and weeks.
 */
@Serializable
data class TrainingPlan(
    val id: String,
    val ageGroupId: String,
    val level: TrainingPlanLevel,
    val periodStart: LocalDate,
    val focus: String? = null,
    val objectives: List<String> = emptyList(),
    val notes: String? = null,
    // WEEK only.
    val intensity: TrainingIntensity? = null,
    // SEASON only.
    val phases: List<TrainingPhase> = emptyList(),
    val updatedAt: Instant? = null
)

/**
 * `GET /age-groups/{ageGroupId}/training-plans` -- every plan of the age group's season, with the
 * season (for its dates) and whether the caller may edit them (`MANAGE_TRAINING`).
 */
@Serializable
data class TrainingPlans(
    val ageGroup: AgeGroup,
    val clubName: String,
    val season: Season,
    val plans: List<TrainingPlan>,
    val canEdit: Boolean
)

/** `PUT /age-groups/{ageGroupId}/training-plans` -- creates or replaces the plan of [level] / [periodStart]. */
@Serializable
data class SaveTrainingPlanRequest(
    val level: TrainingPlanLevel,
    val periodStart: LocalDate,
    val focus: String? = null,
    val objectives: List<String> = emptyList(),
    val notes: String? = null,
    val intensity: TrainingIntensity? = null,
    val phases: List<TrainingPhase> = emptyList()
)

// ---- Periods ----

/** The Monday of [date]'s week (weeks run Monday–Sunday). */
fun mondayOf(date: LocalDate): LocalDate = date.minus(DatePeriod(days = date.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber))

/** The 1st of [date]'s month. */
fun monthStartOf(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

/** The first day of every calendar month the season touches, in order. */
fun Season.monthStarts(): List<LocalDate> {
    val months = mutableListOf<LocalDate>()
    var month = monthStartOf(startDate)
    while (month <= endDate) {
        months += month
        month = month.plus(DatePeriod(months = 1))
    }
    return months
}

/** The Monday of every week that overlaps the season, in order. */
fun Season.weekStarts(): List<LocalDate> {
    val weeks = mutableListOf<LocalDate>()
    var monday = mondayOf(startDate)
    while (monday <= endDate) {
        weeks += monday
        monday = monday.plus(DatePeriod(days = 7))
    }
    return weeks
}

/** A week that crosses two months belongs to the month of its Monday. */
fun weeksOfMonth(season: Season, monthStart: LocalDate): List<LocalDate> =
    season.weekStarts().filter { monthStartOf(it) == monthStart }

/** Where [level]'s period starts for the one containing [date]. */
fun periodStartOf(level: TrainingPlanLevel, date: LocalDate, season: Season): LocalDate = when (level) {
    TrainingPlanLevel.SEASON -> season.startDate
    TrainingPlanLevel.MONTH -> monthStartOf(date)
    TrainingPlanLevel.WEEK -> mondayOf(date)
}

/** Whether [periodStart] is a real period of [level] in [season]. */
fun Season.isPeriodStart(level: TrainingPlanLevel, periodStart: LocalDate): Boolean = when (level) {
    TrainingPlanLevel.SEASON -> periodStart == startDate
    TrainingPlanLevel.MONTH -> periodStart in monthStarts()
    TrainingPlanLevel.WEEK -> periodStart in weekStarts()
}

/** The last day of the period that starts on [periodStart]. */
fun periodEnd(level: TrainingPlanLevel, periodStart: LocalDate, season: Season): LocalDate = when (level) {
    TrainingPlanLevel.SEASON -> season.endDate
    TrainingPlanLevel.MONTH -> periodStart.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
    TrainingPlanLevel.WEEK -> periodStart.plus(DatePeriod(days = 6))
}

/** The plan of [level] for the period starting on [periodStart], if there is one. */
fun List<TrainingPlan>.planFor(level: TrainingPlanLevel, periodStart: LocalDate): TrainingPlan? =
    firstOrNull { it.level == level && it.periodStart == periodStart }
