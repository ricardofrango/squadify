package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.access.scope
import com.sportssoftw.squadcall.domain.repository.MatchEventRepository
import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.service.ClubMatchesBus
import com.sportssoftw.squadcall.domain.service.LiveMatchBus
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.ClubMatchesUpdate
import com.sportssoftw.squadcall.model.LiveMatchUpdate
import com.sportssoftw.squadcall.model.MatchStatus
import kotlin.time.Instant

/**
 * Freezes the running clock for a mid-half stoppage (injury, weather, anything short of a proper
 * HALF_TIME) -- deliberately not a [MatchStatus] transition, since the match is still LIVE the whole
 * time; only [com.sportssoftw.squadcall.model.Match.pausedAt] changes. Same match-day permission gate
 * as kicking off / changing status (open to staff, not just the coach -- see TECHNICAL_DECISIONS.md's
 * "Match-Day Permissions Opened to Staff" section).
 */
class PauseMatchUseCase(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val liveMatchBus: LiveMatchBus,
    private val clubMatchesBus: ClubMatchesBus,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, matchId: String): Boolean = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        accessPolicy.requireManageLiveMatch(userId, match.scope, "Only admins, coordinators, coaches, or team staff can pause a match")

        match.requireLiveManager(userId)

        if (match.status != MatchStatus.LIVE) {
            throw ValidationException("Can only pause a match that is currently live")
        }
        if (match.pausedAt != null) {
            throw ValidationException("Match is already paused")
        }

        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val paused = matchRepository.pauseMatch(matchId, now)
        if (paused) {
            val updatedMatch = match.copy(pausedAt = now)
            val score = matchEventRepository.getScoreByMatch(matchId)
            liveMatchBus.publish(matchId, LiveMatchUpdate(score = score, match = updatedMatch))
            clubMatchesBus.publish(match.clubId, ClubMatchesUpdate(match = updatedMatch))
        }
        paused
    }
}
