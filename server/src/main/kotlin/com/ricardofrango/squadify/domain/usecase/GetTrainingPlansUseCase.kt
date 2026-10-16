package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TrainingPlanRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.TrainingPlans

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
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, ageGroupId: String): TrainingPlans = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Age group not found")

        checkUserPermissionUseCase(
            userId, ageGroup.clubId,
            minClubRole = ClubRole.MEMBER,
            minTeamRole = TeamRole.STAFF,
            ageGroupId = ageGroupId
        )

        val canEdit = try {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = ageGroup.clubId,
                minClubRole = ClubRole.COORDINATOR,
                configurablePermission = ConfigurablePermission.MANAGE_TRAINING,
                ageGroupId = ageGroupId
            )
            true
        } catch (e: ForbiddenException) {
            false
        }

        TrainingPlans(
            ageGroup = ageGroup,
            clubName = clubRepository.getClubById(ageGroup.clubId)?.name ?: throw NotFoundException("Club not found"),
            season = seasonRepository.getSeasonById(ageGroup.seasonId) ?: throw NotFoundException("Season not found"),
            plans = trainingPlanRepository.getByAgeGroup(ageGroupId),
            canEdit = canEdit
        )
    }
}
