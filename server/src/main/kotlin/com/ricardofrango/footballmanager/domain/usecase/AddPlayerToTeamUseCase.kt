package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.AddPlayerToTeamRequest
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.model.TeamPlayer
import kotlin.uuid.Uuid

class AddPlayerToTeamUseCase(
    private val playerRepository: PlayerRepository,
    private val teamRepository: TeamRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        request: AddPlayerToTeamRequest
    ): TeamPlayer {
        // 1. Verify team exists first to get context
        val team = teamRepository.getTeamById(request.teamId)
            ?: throw ValidationException("Team not found")

        // 2. Single granular permission check using team's clubId
        checkUserPermissionUseCase(
            userId = userId,
            clubId = team.clubId,
            minRole = Role.COACH,
            teamId = request.teamId,
            errorMessage = "Insufficient permissions to add players to this team"
        )

        // 3. Get Age Group to check age eligibility
        val ageGroup = ageGroupRepository.getAgeGroupById(team.ageGroupId)
            ?: throw ValidationException("Age Group not found")

        // 4. Verify player exists and belongs to the club
        val player = playerRepository.getPlayerById(request.playerId)
            ?: throw ValidationException("Player not found")

        if (player.clubId != team.clubId) {
            throw ForbiddenException("Player does not belong to this club")
        }

        // 5. Age eligibility check: player.birthYear must be >= ageGroup.birthYear
        if (player.birthDate.year < ageGroup.birthYear) {
            throw ValidationException("Player ${player.firstName} ${player.lastName} is too old for this age group")
        }

        val teamPlayer = TeamPlayer(
            id = Uuid.random().toString(),
            teamId = request.teamId,
            playerId = request.playerId,
            startDate = request.startDate,
            endDate = request.endDate,
            status = request.status
        )

        return playerRepository.addPlayerToTeam(teamPlayer)
    }
}
