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
import com.ricardofrango.squadify.model.CreateTeamsRequest
import com.ricardofrango.squadify.model.Team
import kotlin.uuid.Uuid

/**
 * Batch counterpart of [CreateTeamUseCase]: creates every team in [CreateTeamsRequest.items] under
 * the one season/age group, inside a single transaction so the whole batch either lands or none of
 * it does (mirrors [CreateAgeGroupsUseCase]). Same [AccessPolicy.requireManageTeams] check and same
 * age-group-belongs-to-club-and-season validation as the single-team case.
 */
class CreateTeamsUseCase(
    private val teamRepository: TeamRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val clubRepository: ClubRepository,
    private val fcmTokenRepository: FcmTokenRepository,
    private val accessPolicy: AccessPolicy,
    private val pushNotificationService: PushNotificationService,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: CreateTeamsRequest): List<Team> {
        var ageGroupName = "your age group"
        var clubName = "your club"
        var pushTokens: List<String> = emptyList()

        val createdTeams = transactionManager.runInTransaction {
            if (request.items.isEmpty()) {
                throw ValidationException("At least one team must be provided")
            }

            // The age group must actually belong to the club and season the teams are being created
            // under -- otherwise a team could reference an age group from a different season/club.
            // Validated BEFORE the permission check below, since that check trusts this ageGroupId to
            // resolve the caller's age-group-staff role -- an unvalidated ageGroupId would let a coach
            // of Club A's age group spoof permission to create teams in Club B (same guard as
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

            val club = clubRepository.getClubById(request.clubId)
                ?: throw NotFoundException("Club not found")
            clubName = club.name

            val teamsToCreate = request.items.map { item ->
                if (item.name.isBlank()) {
                    throw ValidationException("Team name cannot be empty")
                }
                if (item.teamSize != null && item.teamSize !in club.sport.teamSizes) {
                    throw ValidationException("Team size ${item.teamSize} is not valid for ${club.sport}")
                }
                Team(
                    id = Uuid.random().toString(),
                    clubId = request.clubId,
                    ageGroupId = request.ageGroupId,
                    seasonId = request.seasonId,
                    name = item.name,
                    code = item.code,
                    active = true,
                    // Fall back to the age group's default when the row didn't override it.
                    teamSize = item.teamSize ?: ageGroup.teamSize
                )
            }

            val created = teamRepository.createTeams(teamsToCreate)

            // clubRepository/fcmTokenRepository have no ambient transaction of their own outside this
            // block -- fetched here, not after the transaction closes, even though only used for the
            // push sent below.
            val memberIds = clubRepository.getClubMembers(request.clubId).map { it.userId }
            pushTokens = fcmTokenRepository.getTokensForUsers(memberIds).values.flatten()

            created
        }

        // Sent after the transaction commits -- a failed/slow push must never roll back team creation.
        if (pushTokens.isNotEmpty()) {
            // One team opens that team; several open their age group, where they're all listed.
            val link = createdTeams.singleOrNull()?.let { PushLink.team(request.clubId, it.id) }
                ?: PushLink.ageGroup(request.clubId, request.ageGroupId)
            pushNotificationService.sendTeamCreated(pushTokens, link, createdTeams.map { it.name }, ageGroupName, clubName)
        }

        return createdTeams
    }
}
