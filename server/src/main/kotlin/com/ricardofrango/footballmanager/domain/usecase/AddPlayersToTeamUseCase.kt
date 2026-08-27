package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.AddPlayersToTeamRequest
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.model.TeamPlayer
import kotlin.uuid.Uuid

class AddPlayersToTeamUseCase(
    private val playerRepository: PlayerRepository,
    private val teamRepository: TeamRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, clubId: String, request: AddPlayersToTeamRequest): List<TeamPlayer> {
        // 1. Verify team exists and belongs to the correct club
        val team = teamRepository.getTeamById(request.teamId)
            ?: throw ValidationException("Team not found")

        if (team.clubId != clubId) {
            throw ForbiddenException("Team does not belong to this club")
        }

        // 2. Get Age Group to check age eligibility
        val ageGroup = ageGroupRepository.getAgeGroupById(team.ageGroupId)
            ?: throw ValidationException("Age Group not found")

        // 3. Check permissions
        val userRole = userRepository.getUserRoleInClub(userId, clubId)
        
        // Admins/Coordinators can do it. Coaches can do it if they are staff of the team.
        val hasPermission = when (userRole) {
            Role.CLUB_ADMIN, Role.COORDINATOR -> true
            Role.COACH -> teamRepository.isUserStaffOfTeam(userId, request.teamId)
            else -> false
        }

        if (!hasPermission) {
            throw ForbiddenException("Insufficient permissions to add players to this team")
        }

        // 4. Get player details in batch
        val playerIds = request.players.map { it.playerId }
        val playersData = playerRepository.getPlayersData(playerIds).associateBy { it.id }

        val teamPlayers = request.players.map {
            val player = playersData[it.playerId]
                ?: throw ValidationException("Player ${it.playerId} not found")
            
            if (player.clubId != clubId) {
                throw ForbiddenException("Player ${it.playerId} does not belong to this club")
            }

            // 5. Age eligibility check: player.birthYear must be >= ageGroup.birthYear
            // Higher birth year means younger player. (e.g. 2017 >= 2016 is OK, 2015 >= 2016 is NO)
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

        return playerRepository.addPlayersToTeam(teamPlayers)
    }
}
