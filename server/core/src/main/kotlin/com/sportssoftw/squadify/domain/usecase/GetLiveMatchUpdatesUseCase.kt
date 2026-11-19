package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.access.AccessPolicy
import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.domain.service.LiveMatchBus
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.exception.NotFoundException
import com.sportssoftw.squadify.model.LiveMatchUpdate
import kotlinx.coroutines.flow.Flow

class GetLiveMatchUpdatesUseCase(
    private val matchRepository: MatchRepository,
    private val liveMatchBus: LiveMatchBus,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, matchId: String): Flow<LiveMatchUpdate> = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId)
            ?: throw NotFoundException("Match not found")

        // 1. Authorize: Member of club can see live updates
        accessPolicy.requireMember(userId, match.clubId)
        accessPolicy.requireLiveInPlan(match.clubId)

        // 2. Return the Reactive Stream
        liveMatchBus.subscribe(matchId)
    }
}
