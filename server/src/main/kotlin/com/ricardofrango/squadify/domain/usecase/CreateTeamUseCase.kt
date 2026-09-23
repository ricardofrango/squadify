package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.PushNotificationService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.CreateTeamRequest
import com.ricardofrango.squadify.model.Team
import kotlin.uuid.Uuid

class CreateTeamUseCase(
    private val teamRepository: TeamRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val clubRepository: ClubRepository,
    private val fcmTokenRepository: FcmTokenRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val pushNotificationService: PushNotificationService,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: CreateTeamRequest): Team {
        var ageGroupName = "your age group"
        var clubName = "your club"
        var pushTokens: List<String> = emptyList()

        val createdTeam = transactionManager.runInTransaction {
            // Permission check: Only CLUB_ADMIN or COORDINATOR can create teams
            checkUserPermissionUseCase(
                userId = userId,
                clubId = request.clubId,
                minClubRole = ClubRole.COORDINATOR,
                errorMessage = "Only admins or coordinators can create teams"
            )

            if (request.name.isBlank()) {
                throw ValidationException("Team name cannot be empty")
            }

            // A team's age group must actually belong to the club and season it's being created under --
            // otherwise a team could reference an age group from a completely different season/club.
            val ageGroup = ageGroupRepository.getAgeGroupById(request.ageGroupId)
                ?: throw NotFoundException("Age group not found")
            if (ageGroup.clubId != request.clubId) {
                throw ValidationException("Age group does not belong to this club")
            }
            if (ageGroup.seasonId != request.seasonId) {
                throw ValidationException("Age group does not belong to the given season")
            }
            ageGroupName = ageGroup.name

            // clubRepository has no ambient transaction of its own outside this block -- fetched here,
            // not after the transaction closes, even though it's only used for the push sent below.
            val club = clubRepository.getClubById(request.clubId)
                ?: throw NotFoundException("Club not found")
            clubName = club.name
            if (request.teamSize != null && request.teamSize !in club.sport.teamSizes) {
                throw ValidationException("Team size ${request.teamSize} is not valid for ${club.sport}")
            }

            val team = Team(
                id = Uuid.random().toString(),
                clubId = request.clubId,
                ageGroupId = request.ageGroupId,
                seasonId = request.seasonId,
                name = request.name,
                code = request.code,
                active = true,
                teamSize = request.teamSize ?: ageGroup.teamSize
            )

            val created = teamRepository.createTeam(team)

            val memberIds = clubRepository.getClubMembers(request.clubId).map { it.userId }
            pushTokens = fcmTokenRepository.getTokensForUsers(memberIds).values.flatten()

            created
        }

        // Sent after the transaction commits -- a failed/slow push must never roll back team creation.
        if (pushTokens.isNotEmpty()) {
            pushNotificationService.sendTeamCreated(pushTokens, listOf(createdTeam.name), ageGroupName, clubName)
        }

        return createdTeam
    }
}
