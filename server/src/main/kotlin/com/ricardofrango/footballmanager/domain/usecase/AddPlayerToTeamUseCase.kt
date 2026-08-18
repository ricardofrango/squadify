package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.model.TeamPlayer

class AddPlayerToTeamUseCase(
    private val playerRepository: PlayerRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, clubId: String, teamPlayer: TeamPlayer): Boolean {
        val userRole = userRepository.getUserRoleInClub(userId, clubId)
        
        // Admins/Coordinators can do it. Coaches can do it if they are staff of the team.
        val hasPermission = when (userRole) {
            Role.CLUB_ADMIN, Role.COORDINATOR -> true
            Role.COACH -> teamRepository.isUserStaffOfTeam(userId, teamPlayer.teamId)
            else -> false
        }

        if (!hasPermission) {
            throw ForbiddenException("Insufficient permissions to add players to this team")
        }

        return playerRepository.addPlayerToTeam(teamPlayer)
    }
}
