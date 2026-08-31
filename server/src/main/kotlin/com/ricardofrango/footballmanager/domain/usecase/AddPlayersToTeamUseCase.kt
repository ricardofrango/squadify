package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.AddPlayersToTeamRequest
import com.ricardofrango.footballmanager.model.TeamPlayer
import com.ricardofrango.footballmanager.model.TeamRole
import kotlin.uuid.Uuid

class AddPlayersToTeamUseCase(
    private val playerRepository: PlayerRepository,
    private val teamRepository: TeamRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        request: AddPlayersToTeamRequest
    ): List<TeamPlayer> = transactionManager.runInTransaction {
        // 1. Verify team exists first to get context
        val team = teamRepository.getTeamById(request.teamId)
            ?: throw ValidationException("Team not found")

        // 2. Single granular permission check
        checkUserPermissionUseCase(
            userId = userId,
            clubId = team.clubId,
            minTeamRole = TeamRole.COACH,
            teamId = request.teamId,
            errorMessage = "Insufficient permissions to add players to this team"
        )

        // 3. Get Age Group to check age eligibility
        val ageGroup = ageGroupRepository.getAgeGroupById(team.ageGroupId)
            ?: throw ValidationException("Age Group not found")

        // 4. Get player details in batch
        val playerIds = request.players.map { it.playerId }
        val playersData = playerRepository.getPlayersData(playerIds).associateBy { it.id }

        val teamPlayers = request.players.map {
            val player = playersData[it.playerId]
                ?: throw ValidationException("Player ${it.playerId} not found")

            if (player.clubId != team.clubId) {
                throw ForbiddenException("Player ${it.playerId} does not belong to this club")
            }

            // 5. Age eligibility check
            if (player.birthDate.year < ageGroup.birthYear) {
                throw ValidationException("Player ${player.firstName} ${player.lastName} is too old for this age group")
            }

            TeamPlayer(
                id = Uuid.random().toString(),
                teamId = request.teamId,
                playerId = it.playerId,
                startDate = it.startDate,
                endDate = it.endDate,
                status = it.status
            )
        }

        playerRepository.addPlayersToTeam(teamPlayers)
    }
}
