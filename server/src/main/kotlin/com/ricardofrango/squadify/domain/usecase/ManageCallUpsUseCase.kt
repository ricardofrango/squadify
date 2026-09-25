package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.*

class ManageCallUpsUseCase(
    private val matchRepository: MatchRepository,
    private val transactionManager: TransactionManager,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
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
            configurablePermission = ConfigurablePermission.MANAGE_LIVE_MATCH,
            teamId = match.teamId,
            errorMessage = "Insufficient permissions to manage call-ups"
        )

        matchRepository.manageCallUps(matchId, request.playerIds)
    }
}
