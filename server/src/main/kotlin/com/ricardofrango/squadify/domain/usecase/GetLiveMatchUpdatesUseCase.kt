package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.LiveMatchUpdate
import kotlinx.coroutines.flow.Flow

class GetLiveMatchUpdatesUseCase(
    private val matchRepository: MatchRepository,
    private val liveMatchBus: LiveMatchBus,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, matchId: String): Flow<LiveMatchUpdate> = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId)
            ?: throw NotFoundException("Match not found")

        // 1. Authorize: Member of club can see live updates
        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minClubRole = ClubRole.MEMBER
        )

        // 2. Return the Reactive Stream
        liveMatchBus.subscribe(matchId)
    }
}
