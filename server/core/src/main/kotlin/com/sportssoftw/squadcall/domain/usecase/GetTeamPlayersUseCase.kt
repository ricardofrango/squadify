package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.access.scope
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.model.Player

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
