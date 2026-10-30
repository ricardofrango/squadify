package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.CoachSpace
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.Team
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * The first setup, right after signing up (REQUIREMENTS §5c): the coach's space, owned by them, with
 * its first age group and team in the current season. One space per account for now, so a coach who
 * already belongs to one is refused.
 */
class SetUpSpaceUseCase(
    private val clubRepository: ClubRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val teamRepository: TeamRepository,
    private val coachSeasons: CoachSeasons,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: SetUpSpaceRequest): SpaceSetup = transactionManager.runInTransaction {
        if (clubRepository.getMemberships(userId).isNotEmpty()) {
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

/** The space the coach belongs to: their own, or the first they were added to. 404 before the first setup. */
class GetSpaceUseCase(
    private val clubRepository: ClubRepository,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String): CoachSpace = transactionManager.runInTransaction {
        val memberships = clubRepository.getMemberships(userId)
        val (space, role) = memberships.firstOrNull { it.second == ClubRole.CLUB_ADMIN }
            ?: memberships.firstOrNull()
            ?: throw NotFoundException("No space yet")
        CoachSpace(space, isOwner = role == ClubRole.CLUB_ADMIN)
    }
}
