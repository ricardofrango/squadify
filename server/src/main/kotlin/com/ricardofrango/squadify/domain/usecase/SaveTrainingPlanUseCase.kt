package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TrainingPlanRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.SaveTrainingPlanRequest
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.TrainingKeyDate
import com.ricardofrango.squadify.model.TrainingPhase
import com.ricardofrango.squadify.model.TrainingPlan
import com.ricardofrango.squadify.model.TrainingPlanLevel
import com.ricardofrango.squadify.model.isPeriodStart
import com.ricardofrango.squadify.model.planFor
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/**
 * Creates or replaces one training plan of an age group (design 40), behind `MANAGE_TRAINING`.
 * - The period must be one of the season's: its start date (season), the 1st of a month it touches
 *   (month) or the Monday of a week that overlaps it (week).
 * - Months and weeks need a focus. Intensity is for weeks, phases and key dates for the season.
 * - Phase and key-date dates fall inside the season, and none ends before it starts.
 * - Text is trimmed; blank objectives, a blank focus and blank notes are dropped.
 */
class SaveTrainingPlanUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val seasonRepository: SeasonRepository,
    private val trainingPlanRepository: TrainingPlanRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, ageGroupId: String, request: SaveTrainingPlanRequest): TrainingPlan = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Age group not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = ageGroup.clubId,
            minClubRole = ClubRole.COORDINATOR,
            configurablePermission = ConfigurablePermission.MANAGE_TRAINING,
            ageGroupId = ageGroupId,
            errorMessage = "Only coordinators, or this age group's staff the club allows, can plan its training"
        )

        val season = seasonRepository.getSeasonById(ageGroup.seasonId) ?: throw NotFoundException("Season not found")
        if (!season.isPeriodStart(request.level, request.periodStart)) {
            throw ValidationException("That date doesn't start a ${request.level.name.lowercase()} of the season")
        }

        val focus = request.focus.cleaned()
        if (request.level != TrainingPlanLevel.SEASON && focus == null) throw ValidationException("A focus is required")
        if (request.level != TrainingPlanLevel.WEEK && request.intensity != null) throw ValidationException("Only a week has an intensity")
        if (request.level != TrainingPlanLevel.SEASON && (request.phases.isNotEmpty() || request.keyDates.isNotEmpty())) {
            throw ValidationException("Only the season plan has phases and key dates")
        }

        val phases = request.phases.map { phase ->
            val name = phase.name.cleaned() ?: throw ValidationException("Every phase needs a name")
            requireRange(phase.startDate, phase.endDate, season)
            TrainingPhase(name, phase.startDate, phase.endDate, phase.focus.cleaned())
        }
        val keyDates = request.keyDates.map { keyDate ->
            val label = keyDate.label.cleaned() ?: throw ValidationException("Every key date needs a label")
            requireRange(keyDate.startDate, keyDate.endDate ?: keyDate.startDate, season)
            TrainingKeyDate(keyDate.startDate, keyDate.endDate?.takeIf { it != keyDate.startDate }, label)
        }

        val existing = trainingPlanRepository.getByAgeGroup(ageGroupId).planFor(request.level, request.periodStart)
        trainingPlanRepository.save(
            TrainingPlan(
                id = existing?.id ?: Uuid.random().toString(),
                ageGroupId = ageGroupId,
                level = request.level,
                periodStart = request.periodStart,
                focus = focus,
                objectives = request.objectives.mapNotNull { it.cleaned() },
                notes = request.notes.cleaned(),
                intensity = request.intensity,
                phases = phases,
                keyDates = keyDates
            ),
            updatedBy = userId
        )
    }

    private fun requireRange(start: LocalDate, end: LocalDate, season: Season) {
        if (end < start) throw ValidationException("An end date can't be before its start")
        if (start < season.startDate || end > season.endDate) {
            throw ValidationException("Dates must fall inside the season (${season.startDate} – ${season.endDate})")
        }
    }
}
