package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.ConfigurablePermission

/**
 * "Removes" a player from the club, from the club's Players screen: the player is marked disabled
 * (`active = false`) and dropped from the call-ups of matches that haven't started. Nothing else changes:
 * they stay in their age groups' pools, their data and match history stay, and they show as disabled. Behind the club-level
 * `MANAGE_PLAYER_STATUS` setting (admins and coordinators by default, or admins only), since it's
 * club-wide. A disabled player can't be added to a pool or called up.
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
        dropFromUpcomingCallUps(playerId, player.clubId, ageGroupIds = null, teamRepository, matchRepository)
    }
}
