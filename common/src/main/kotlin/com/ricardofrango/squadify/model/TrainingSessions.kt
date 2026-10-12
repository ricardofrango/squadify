package com.ricardofrango.squadify.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlin.time.Instant

// Training sessions (design 39): an age group's trainings, created in bulk from weekly slots over a
// period (or one at a time), cancelled one by one, with exercises and staff-only player ratings.

/**
 * One training. [cancelledAt] is set while it's cancelled; it stays on the calendar and keeps its content.
 * [exerciseCount] is for the list rows. [ratedCount] / [averageRating] are staff-only, null for others.
 */
@Serializable
data class TrainingSession(
    val id: String,
    val ageGroupId: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val location: String? = null,
    val notes: String? = null,
    val cancelledAt: Instant? = null,
    val cancelledByName: String? = null,
    val exerciseCount: Int = 0,
    val ratedCount: Int? = null,
    val averageRating: Double? = null
) {
    val cancelled: Boolean get() = cancelledAt != null
}

/** A weekly slot: one or more weekdays (ISO numbers, Monday = 1) sharing a start and an end time. */
@Serializable
data class TrainingSlot(
    val weekdays: List<Int>,
    val startTime: LocalTime,
    val endTime: LocalTime
)

/** `POST /age-groups/{id}/training-sessions/preview` and `/bulk`. Nothing about it is saved for next time. */
@Serializable
data class CreateTrainingSessionsRequest(
    val slots: List<TrainingSlot>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val location: String? = null,
    val includeHolidays: Boolean = false,
    // Required to create when a session with exercises or ratings would be replaced (the app confirms first).
    val confirmReplacingContent: Boolean = false
)

/** A session that already exists on the same date and start time, and what replacing it deletes. */
@Serializable
data class ReplacedSession(
    val sessionId: String,
    val exerciseCount: Int,
    val ratingCount: Int
) {
    val hasContent: Boolean get() = exerciseCount > 0 || ratingCount > 0
}

/** One date of the preview: a session to create (maybe replacing one), or a skipped holiday. */
@Serializable
data class TrainingSessionPreviewItem(
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val skippedHoliday: Holiday? = null,
    val replaces: ReplacedSession? = null
)

@Serializable
data class TrainingSessionsPreview(
    val items: List<TrainingSessionPreviewItem>
) {
    val toCreate: List<TrainingSessionPreviewItem> get() = items.filter { it.skippedHoliday == null }
    val skippedHolidays: Int get() = items.count { it.skippedHoliday != null }
    val replacing: Int get() = toCreate.count { it.replaces != null }
    val replacingWithContent: Int get() = toCreate.count { it.replaces?.hasContent == true }
}

/** `POST /age-groups/{id}/training-sessions` and `PATCH /training-sessions/{id}`: one session. */
@Serializable
data class SaveTrainingSessionRequest(
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val location: String? = null,
    val notes: String? = null
)

/**
 * `GET /age-groups/{id}/training-sessions`: the season's sessions of an age group, newest last, with what
 * the Training tab needs around them. [canEdit] follows `MANAGE_TRAINING`; [canRate] is any coach or
 * staff member of the age group (and coordinators).
 */
@Serializable
data class TrainingSessions(
    val ageGroup: AgeGroup,
    val clubName: String,
    val season: Season,
    val stadiumLocation: String? = null,
    val country: Country = Country.DEFAULT,
    val sessions: List<TrainingSession>,
    val canEdit: Boolean,
    val canRate: Boolean
)

/** An exercise of an age group's library (design 39h). [usedCount] is how many sessions use it. */
@Serializable
data class TrainingExercise(
    val id: String,
    val ageGroupId: String,
    val name: String,
    val description: String? = null,
    val durationMinutes: Int? = null,
    val objectives: String? = null,
    val usedCount: Int = 0
)

/** `POST /age-groups/{id}/exercises` and `PATCH /exercises/{id}`. */
@Serializable
data class SaveExerciseRequest(
    val name: String,
    val description: String? = null,
    val durationMinutes: Int? = null,
    val objectives: String? = null
)

/** `PUT /training-sessions/{id}/exercises`: the session's exercises, in order (adds, removes and reorders). */
@Serializable
data class SetSessionExercisesRequest(val exerciseIds: List<String>)

/**
 * `GET /training-sessions/{id}`: one session (design 39f) with its age group, how many players its pool
 * has (for "12 of 18 rated"), and the week and month plans it falls in (design 40f, staff only).
 */
@Serializable
data class TrainingSessionDetails(
    val session: TrainingSession,
    val ageGroup: AgeGroup,
    val clubName: String,
    val season: Season,
    val poolSize: Int,
    val weekPlan: TrainingPlan? = null,
    val monthPlan: TrainingPlan? = null,
    // In order.
    val exercises: List<TrainingExercise> = emptyList(),
    val canEdit: Boolean,
    val canRate: Boolean
)

/** The most sessions one bulk creation may produce. */
const val MAX_BULK_SESSIONS = 400

/**
 * Every date in [startDate]..[endDate] that falls on one of the slots' weekdays, in date then time order.
 * Holidays in [holidays] are kept as skipped items unless [includeHolidays].
 */
fun plannedSessions(
    slots: List<TrainingSlot>,
    startDate: LocalDate,
    endDate: LocalDate,
    holidays: Map<LocalDate, Holiday>,
    includeHolidays: Boolean
): List<TrainingSessionPreviewItem> {
    val items = mutableListOf<TrainingSessionPreviewItem>()
    var date = startDate
    while (date <= endDate) {
        val day = date.dayOfWeek.isoDayNumber
        slots.filter { day in it.weekdays }.sortedBy { it.startTime }.forEach { slot ->
            val holiday = holidays[date].takeIf { !includeHolidays }
            items += TrainingSessionPreviewItem(date, slot.startTime, slot.endTime, skippedHoliday = holiday)
        }
        date = date.plus(DatePeriod(days = 1))
    }
    return items
}
