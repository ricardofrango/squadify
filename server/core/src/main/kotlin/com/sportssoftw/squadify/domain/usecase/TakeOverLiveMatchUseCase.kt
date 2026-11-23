package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.access.AccessPolicy
import com.sportssoftw.squadify.domain.access.scope
import com.sportssoftw.squadify.domain.repository.MatchEventRepository
import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.domain.service.LiveMatchBus
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.exception.NotFoundException
import com.sportssoftw.squadify.exception.ValidationException
import com.sportssoftw.squadify.model.LiveMatchUpdate
import com.sportssoftw.squadify.model.isInPlay

/**
 * Someone else with the live-match permission takes the game over (the developer, 2026-11-07): when
 * whoever kicked it off can't go on (a dead phone, they left). From then on only they log events,
 * change the pitch and run the clock (requireLiveManager); the previous manager's phone hears it on
 * the live socket and only watches. Only while the game is on.
 */
class TakeOverLiveMatchUseCase(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val liveMatchBus: LiveMatchBus,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, matchId: String): Unit = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        accessPolicy.requireManageLiveMatch(userId, match.scope, "Only admins, coordinators, coaches, or team staff can manage a match")

        if (!match.isInPlay) {
            throw ValidationException("A match can only be taken over while it's on")
        }
        if (match.liveManagerUserId == userId) return@runInTransaction

        matchRepository.setLiveManager(matchId, userId)
        val score = matchEventRepository.getScoreByMatch(matchId)
        liveMatchBus.publish(matchId, LiveMatchUpdate(score = score, match = match.copy(liveManagerUserId = userId)))
    }
}
