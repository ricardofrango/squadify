package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission

/**
 * "Removes" a player from the club, from the club's Players screen: the player is marked disabled
 * (`active = false`) and taken out of every age group's pool, but nothing is deleted -- their data and
 * match history stay, and the Players screen keeps listing them as disabled. Behind the club-level
 * `MANAGE_PLAYER_STATUS` setting (admins and coordinators by default, or admins only), since it's
 * club-wide. A disabled player can't be added to a pool.
 */
class DisablePlayerUseCase(
    private val playerRepository: PlayerRepository,
    private val teamRepository: TeamRepository,
    private val matchRepository: MatchRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, playerId: String) = transactionManager.runInTransaction {
        val player = playerRepository.getPlayerById(playerId) ?: throw NotFoundException("Player not found")
        checkUserPermissionUseCase(
            userId = userId,
            clubId = player.clubId,
            minClubRole = ClubRole.COORDINATOR,
            configurablePermission = ConfigurablePermission.MANAGE_PLAYER_STATUS,
            errorMessage = "You aren't allowed to disable players in this club"
        )
        playerRepository.updateActiveStatus(playerId, active = false)
        val ageGroupIds = playerRepository.removePlayerFromAllAgeGroups(playerId)
        dropFromUpcomingCallUps(playerId, player.clubId, ageGroupIds.toSet(), teamRepository, matchRepository)
    }
}
