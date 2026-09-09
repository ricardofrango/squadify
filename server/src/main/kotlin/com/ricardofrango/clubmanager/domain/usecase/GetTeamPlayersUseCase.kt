package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.domain.repository.TeamRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.ValidationException
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.TeamRole
import com.ricardofrango.clubmanager.model.Player

/**
 * "The players available to a team" is now the pool of its **age group** -- teams don't own players.
 */
class GetTeamPlayersUseCase(
    private val playerRepository: PlayerRepository,
    private val teamRepository: TeamRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, teamId: String): List<Player> = transactionManager.runInTransaction {
        val team = teamRepository.getTeamById(teamId) ?: throw ValidationException("Team not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = team.clubId,
            minClubRole = ClubRole.MEMBER,
            minTeamRole = TeamRole.STAFF,
            teamId = teamId,
            allowPlayerOrParentAccess = true
        )

        playerRepository.getPlayersByAgeGroup(team.ageGroupId)
    }
}
