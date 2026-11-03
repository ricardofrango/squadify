package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.PlansResponse
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.Team
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * The first setup, right after signing up (REQUIREMENTS §5c): the coach's space, owned by them, with
 * its first age group and team in the current season. A coach owns one space at most, so a second is
 * refused; being staff in someone else's doesn't count.
 */
class SetUpSpaceUseCase(
    private val clubRepository: ClubRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val teamRepository: TeamRepository,
    private val coachSeasons: CoachSeasons,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: SetUpSpaceRequest): SpaceSetup = transactionManager.runInTransaction {
        if (clubRepository.getMemberships(userId).any { it.second == ClubRole.CLUB_ADMIN }) {
            throw ConflictException("You already have a space")
        }
        val name = request.name.trim().takeIf { it.isNotEmpty() } ?: throw ValidationException("The space's name is required")
        val item = request.ageGroup
        val ageGroupName = item.name.trim().takeIf { it.isNotEmpty() } ?: throw ValidationException("Age group name cannot be empty")
        if (item.startYear < item.endYear) throw ValidationException("Start year cannot be before end year")
        val teamName = request.teamName.trim().takeIf { it.isNotEmpty() } ?: throw ValidationException("Team name cannot be empty")
        val sizes = request.sport.teamSizes
        listOfNotNull(item.teamSize, request.teamSize).firstOrNull { it !in sizes }?.let {
            throw ValidationException("Team size $it is not valid for ${request.sport}")
        }

        val space = clubRepository.createClub(
            Club(
                id = Uuid.random().toString(),
                name = name,
                status = ClubStatus.ACTIVE,
                sport = request.sport,
                createdAt = Clock.System.now()
            )
        )
        clubRepository.addMember(space.id, userId, ClubRole.CLUB_ADMIN)
        val season = coachSeasons.current(space.id)
        val ageGroup = ageGroupRepository.createAgeGroups(
            listOf(
                AgeGroup(
                    id = Uuid.random().toString(),
                    clubId = space.id,
                    name = ageGroupName,
                    seasonId = season.id,
                    startYear = item.startYear,
                    endYear = item.endYear,
                    teamSize = item.teamSize
                )
            )
        ).single()
        val team = teamRepository.createTeam(
            Team(
                id = Uuid.random().toString(),
                clubId = space.id,
                ageGroupId = ageGroup.id,
                seasonId = season.id,
                name = teamName,
                code = null,
                active = true,
                teamSize = request.teamSize ?: item.teamSize
            )
        )
        SpaceSetup(space, season, ageGroup, team)
    }
}

/**
 * Every space the coach is in, their own first, each with the age groups they have there: all of their
 * own space's, and the ones they're staff on in the others, with the space's plan and which of them it
 * locks, and for their own how many age groups it has this season. Empty before they set up or join one.
 */
class GetSpacesUseCase(
    private val clubRepository: ClubRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val transactionManager: TransactionManager,
    private val plans: CoachPlans,
    private val coachSeasons: CoachSeasons
) {
    suspend operator fun invoke(userId: String): List<SpaceAgeGroups> = transactionManager.runInTransaction {
        clubRepository.getMemberships(userId)
            .sortedByDescending { it.second == ClubRole.CLUB_ADMIN }
            .map { (space, role) ->
                val isOwner = role == ClubRole.CLUB_ADMIN
                val ageGroups = ageGroupRepository.getAgeGroupsByClub(space.id)
                    .filter { isOwner || ageGroupRepository.getUserRoleInAgeGroup(userId, it.id) != null }
                val plan = plans.planOf(space.id)
                val lockedAgeGroups = plans.lockedAgeGroups(space.id, plan)
                val locked = ageGroups.filter { it.id in lockedAgeGroups || (!isOwner && userId in plans.lockedStaff(it.id, plan)) }
                val thisSeason = if (isOwner) coachSeasons.currentOrNull(space.id)?.let { season -> ageGroups.count { it.seasonId == season.id } } ?: 0 else 0
                val left = if (isOwner) PlanLimits.ageGroups(plan)?.let { (it - thisSeason).coerceAtLeast(0) } else null
                SpaceAgeGroups(space, isOwner, ageGroups, plan, locked.map { it.id }, thisSeason, left)
            }
    }
}

/**
 * Every plan as the app shows it (`GET /v1/plans`, PlanLimits), its features in [language], and the plan
 * of the space the coach owns (null: they own none).
 */
class GetPlansUseCase(
    private val clubRepository: ClubRepository,
    private val plans: CoachPlans,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, language: String?): PlansResponse = transactionManager.runInTransaction {
        val own = clubRepository.getMemberships(userId).firstOrNull { (_, role) -> role == ClubRole.CLUB_ADMIN }?.first
        PlansResponse(PlanLimits.catalogue(language), own?.let { plans.planOf(it.id) })
    }
}
