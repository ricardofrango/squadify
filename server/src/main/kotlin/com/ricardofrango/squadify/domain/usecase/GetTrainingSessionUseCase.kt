package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TrainingExerciseRepository
import com.ricardofrango.squadify.domain.repository.TrainingPlanRepository
import com.ricardofrango.squadify.domain.repository.TrainingRatingRepository
import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.TrainingPlanLevel
import com.ricardofrango.squadify.model.TrainingSessionDetails
import com.ricardofrango.squadify.model.monthStartOf
import com.ricardofrango.squadify.model.mondayOf
import com.ricardofrango.squadify.model.planFor

/**
 * One training session (design 39f), readable like the Training tab (players and parents too). The week
 * and month plans it falls in come along for the strip at the top (design 40f), only for those who rate.
 */
class GetTrainingSessionUseCase(
    private val trainingSessionRepository: TrainingSessionRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val clubRepository: ClubRepository,
    private val seasonRepository: SeasonRepository,
    private val playerRepository: PlayerRepository,
    private val trainingPlanRepository: TrainingPlanRepository,
    private val trainingExerciseRepository: TrainingExerciseRepository,
    private val trainingRatingRepository: TrainingRatingRepository,
    private val userRepository: UserRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, sessionId: String): TrainingSessionDetails = transactionManager.runInTransaction {
        val stored = trainingSessionRepository.getById(sessionId) ?: throw NotFoundException("Training session not found")
        val ageGroup = ageGroupRepository.getAgeGroupById(stored.session.ageGroupId) ?: throw NotFoundException("Age group not found")
        checkUserPermissionUseCase(
            userId, ageGroup.clubId,
            minClubRole = ClubRole.MEMBER,
            minAgeGroupRole = AgeGroupRole.STAFF,
            ageGroupId = ageGroup.id,
            allowPlayerOrParentAccess = true
        )
        val canEdit = allowed { checkUserPermissionUseCase.requireManageTraining(userId, ageGroup) }
        val canRate = allowed { checkUserPermissionUseCase.requireRateTraining(userId, ageGroup) }
        val plans = if (canRate) trainingPlanRepository.getByAgeGroup(ageGroup.id) else emptyList()
        val date = stored.session.date
        val counts = trainingSessionRepository.contentCounts(listOf(sessionId))[sessionId]

        TrainingSessionDetails(
            session = stored.session.copy(
                cancelledByName = stored.cancelledBy?.let { id -> userRepository.findById(id)?.let { "${it.firstName} ${it.lastName}" } },
                exerciseCount = counts?.exercises ?: 0
            ).withRatings(canRate, if (canRate) trainingRatingRepository.getForSession(sessionId) else emptyList()),
            ageGroup = ageGroup,
            clubName = clubRepository.getClubById(ageGroup.clubId)?.name ?: throw NotFoundException("Club not found"),
            season = seasonRepository.getSeasonById(ageGroup.seasonId) ?: throw NotFoundException("Season not found"),
            poolSize = playerRepository.getPlayersByAgeGroup(ageGroup.id).size,
            weekPlan = plans.planFor(TrainingPlanLevel.WEEK, mondayOf(date)),
            monthPlan = plans.planFor(TrainingPlanLevel.MONTH, monthStartOf(date)),
            exercises = trainingExerciseRepository.getForSession(sessionId),
            canEdit = canEdit,
            canRate = canRate
        )
    }
}
