package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.access.AccessPolicy
import com.sportssoftw.squadify.domain.access.scope
import com.sportssoftw.squadify.domain.repository.PlayerRepository
import com.sportssoftw.squadify.domain.repository.TeamRepository
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.exception.NotFoundException
import com.sportssoftw.squadify.model.Player

/**
 * "The players available to a team" is now the pool of its **age group** -- teams don't own players.
 */
class GetTeamPlayersUseCase(
    private val playerRepository: PlayerRepository,
    private val teamRepository: TeamRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, teamId: String): List<Player> = transactionManager.runInTransaction {
        val team = teamRepository.getTeamById(teamId) ?: throw NotFoundException("Team not found")

        accessPolicy.requireViewGames(userId, team.scope)

        playerRepository.getPlayersByAgeGroup(team.ageGroupId)
    }
}
