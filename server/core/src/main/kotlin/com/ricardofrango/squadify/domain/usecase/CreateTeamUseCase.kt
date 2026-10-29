package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.GameScope
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.PushNotificationService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.PushLink
import com.ricardofrango.squadify.model.CreateTeamRequest
import com.ricardofrango.squadify.model.Team
import kotlin.uuid.Uuid

class CreateTeamUseCase(
    private val teamRepository: TeamRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val clubRepository: ClubRepository,
    private val fcmTokenRepository: FcmTokenRepository,
    private val accessPolicy: AccessPolicy,
    private val pushNotificationService: PushNotificationService,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: CreateTeamRequest): Team {
        var ageGroupName = "your age group"
        var clubName = "your club"
        var pushTokens: List<String> = emptyList()

        val createdTeam = transactionManager.runInTransaction {
            if (request.name.isBlank()) {
                throw ValidationException("Team name cannot be empty")
            }

            // A team's age group must actually belong to the club and season it's being created under --
            // otherwise a team could reference an age group from a completely different season/club.
            // Validated BEFORE the permission check below, since that check trusts this ageGroupId to
            // resolve the caller's age-group-staff role -- an unvalidated ageGroupId would let a coach
            // of Club A's age group spoof permission to create a team in Club B (same guard as
            // CreatePlayersUseCase).
            val ageGroup = ageGroupRepository.getAgeGroupById(request.ageGroupId)
                ?: throw NotFoundException("Age group not found")
            if (ageGroup.clubId != request.clubId) {
                throw ValidationException("Age group does not belong to this club")
            }
            if (ageGroup.seasonId != request.seasonId) {
                throw ValidationException("Age group does not belong to the given season")
            }
            ageGroupName = ageGroup.name

            accessPolicy.requireManageTeams(
                userId,
                GameScope(request.clubId, ageGroupId = request.ageGroupId),
                "Only admins, coordinators, or this age group's coaches can create teams"
            )

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
            pushNotificationService.sendTeamCreated(pushTokens, PushLink.team(request.clubId, createdTeam.id), listOf(createdTeam.name), ageGroupName, clubName)
        }

        return createdTeam
    }
}
