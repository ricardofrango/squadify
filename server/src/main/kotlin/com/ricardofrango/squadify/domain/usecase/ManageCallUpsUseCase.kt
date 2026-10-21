package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.*

class ManageCallUpsUseCase(
    private val matchRepository: MatchRepository,
    private val transactionManager: TransactionManager,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(
        userId: String,
        matchId: String,
        request: ManageCallUpRequest
    ): Boolean = transactionManager.runInTransaction {
        // 1. Fetch match first to get context (required for granular check)
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        // 2. Single permission check with full context from match
        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minClubRole = ClubRole.COORDINATOR,
            configurablePermission = ConfigurablePermission.MANAGE_CALL_UPS,
            teamId = match.teamId,
            errorMessage = "Insufficient permissions to manage call-ups"
        )

        // A disabled player can't be newly called up (one already in the list can stay, e.g. a played match).
        val alreadyIn = matchRepository.getCallUpsForMatch(matchId).map { it.player.id }.toSet()
        val added = request.playerIds.filter { it !in alreadyIn }
        if (added.isNotEmpty()) {
            playerRepository.getPlayersData(added).firstOrNull { !it.active }?.let {
                throw ValidationException("${it.firstName} ${it.lastName} is disabled and can't be called up")
            }
        }

        matchRepository.manageCallUps(matchId, request.playerIds)
    }
}
