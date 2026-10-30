package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.scope
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.Player

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
