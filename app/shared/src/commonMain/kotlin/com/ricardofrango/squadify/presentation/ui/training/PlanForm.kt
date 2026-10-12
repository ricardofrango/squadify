package com.ricardofrango.squadify.presentation.ui.training

import com.ricardofrango.squadify.model.SaveTrainingPlanRequest
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.TrainingIntensity
import com.ricardofrango.squadify.model.TrainingKeyDate
import com.ricardofrango.squadify.model.TrainingPhase
import com.ricardofrango.squadify.model.TrainingPlan
import com.ricardofrango.squadify.model.TrainingPlanLevel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

// The plan editors (design 40c/d/e): one form shape for the three levels. The season uses phases and
// key dates, the week uses intensity; the server refuses the rest anyway.

data class PlanForm(
    val focus: String = "",
    val objectives: List<String> = emptyList(),
    val notes: String = "",
    val intensity: TrainingIntensity? = null,
    val phases: List<TrainingPhase> = emptyList(),
    val keyDates: List<TrainingKeyDate> = emptyList()
)

/** What a date of a phase or key date gets wrong, shown under it. */
enum class PlanDateError { BEFORE_SEASON, AFTER_SEASON, END_BEFORE_START }

/** The form for an existing plan, or an empty one. */
internal fun TrainingPlan?.toForm(): PlanForm = if (this == null) PlanForm() else PlanForm(
    focus = focus.orEmpty(),
    objectives = objectives,
    notes = notes.orEmpty(),
    intensity = intensity,
    phases = phases,
    keyDates = keyDates
)

internal fun PlanForm.toRequest(level: TrainingPlanLevel, periodStart: LocalDate) = SaveTrainingPlanRequest(
    level = level,
    periodStart = periodStart,
    focus = focus.trim().ifEmpty { null },
    objectives = objectives.map { it.trim() }.filter { it.isNotEmpty() },
    notes = notes.trim().ifEmpty { null },
    intensity = intensity.takeIf { level == TrainingPlanLevel.WEEK },
    phases = phases.takeIf { level == TrainingPlanLevel.SEASON }.orEmpty(),
    keyDates = keyDates.takeIf { level == TrainingPlanLevel.SEASON }.orEmpty()
)

/** The error of a start–end range in [season], if any (the start is checked first). */
internal fun rangeError(start: LocalDate, end: LocalDate, season: Season): PlanDateError? = when {
    start < season.startDate -> PlanDateError.BEFORE_SEASON
    end > season.endDate -> PlanDateError.AFTER_SEASON
    end < start -> PlanDateError.END_BEFORE_START
    else -> null
}

/** Whether the form can be saved: months and weeks need a focus, and every date must be valid. */
internal fun PlanForm.isValid(level: TrainingPlanLevel, season: Season): Boolean =
    (level == TrainingPlanLevel.SEASON || focus.isNotBlank()) &&
        phases.all { it.name.isNotBlank() && rangeError(it.startDate, it.endDate, season) == null } &&
        keyDates.all { it.label.isNotBlank() && rangeError(it.startDate, it.endDate ?: it.startDate, season) == null }

/** A new phase starts the day after the last one ends (or with the season) and runs to the season's end. */
internal fun PlanForm.withNewPhase(season: Season): PlanForm {
    val start = phases.lastOrNull()?.endDate?.plus(DatePeriod(days = 1))?.takeIf { it <= season.endDate } ?: season.startDate
    return copy(phases = phases + TrainingPhase("", start, season.endDate))
}

/**
 * "Copy from previous": the previous period's focus, objectives, notes and intensity. When the form
 * already has something typed, the editor asks first (design 40i), since this replaces it.
 */
internal fun PlanForm.copiedFrom(previous: TrainingPlan): PlanForm = copy(
    focus = previous.focus.orEmpty(),
    objectives = previous.objectives,
    notes = previous.notes.orEmpty(),
    intensity = previous.intensity ?: intensity
)

/** Whether "Copy from previous" would change anything already typed (then it asks first). */
internal fun PlanForm.hasTypedContent(): Boolean =
    focus.isNotBlank() || objectives.any { it.isNotBlank() } || notes.isNotBlank()

/** Moves the item at [from] to [to] (reordering objectives and phases). */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val list = toMutableList()
    list.add(to, list.removeAt(from))
    return list
}
