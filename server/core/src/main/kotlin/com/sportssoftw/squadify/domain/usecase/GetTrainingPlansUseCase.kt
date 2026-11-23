package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.access.AccessPolicy
import com.sportssoftw.squadify.domain.access.allowed
import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.domain.repository.ClubRepository
import com.sportssoftw.squadify.domain.repository.SeasonRepository
import com.sportssoftw.squadify.domain.repository.TrainingPlanRepository
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.exception.NotFoundException
import com.sportssoftw.squadify.model.TrainingPlans

/**
 * An age group's training plans (design 40): its season, every season / month / week plan, and whether
 * the caller may edit them. Plans are for the club's people and the age group's staff only -- players
 * and parents never see them, so there's no player/parent access path.
 */
class GetTrainingPlansUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val seasonRepository: SeasonRepository,
    private val clubRepository: ClubRepository,
    private val trainingPlanRepository: TrainingPlanRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, ageGroupId: String): TrainingPlans = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Age group not found")

        accessPolicy.requireViewTrainingPlans(userId, ageGroup.scope)
        accessPolicy.requireTrainingInPlan(ageGroup.clubId)

        val canEdit = allowed { accessPolicy.requireManageTraining(userId, ageGroup.scope) }

        TrainingPlans(
            ageGroup = ageGroup,
            clubName = clubRepository.getClubById(ageGroup.clubId)?.name ?: throw NotFoundException("Club not found"),
            season = seasonRepository.getSeasonById(ageGroup.seasonId) ?: throw NotFoundException("Season not found"),
            plans = trainingPlanRepository.getByAgeGroup(ageGroupId),
            canEdit = canEdit
        )
    }
}
