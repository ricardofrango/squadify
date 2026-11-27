package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.ConfigurablePermission

/**
 * Undoes [DisablePlayerUseCase]: the player is active in the club again, so they can be added to an
 * age group's pool. The pools they were taken out of aren't restored -- whoever manages the age group
 * adds them back. Behind `MANAGE_PLAYER_STATUS`, like disabling. Reactivating an active player is a no-op.
 */
class ReactivatePlayerUseCase(
    private val playerRepository: PlayerRepository,
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
            errorMessage = "You aren't allowed to reactivate players in this club"
        )
        if (!player.active) playerRepository.updateActiveStatus(playerId, active = true)
    }
}
