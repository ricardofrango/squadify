package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.scope
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubMatchesUpdate
import com.ricardofrango.squadify.model.LiveMatchUpdate
import com.ricardofrango.squadify.model.MatchStatus
import kotlin.time.Instant

/**
 * Freezes the running clock for a mid-half stoppage (injury, weather, anything short of a proper
 * HALF_TIME) -- deliberately not a [MatchStatus] transition, since the match is still LIVE the whole
 * time; only [com.ricardofrango.squadify.model.Match.pausedAt] changes. Same match-day permission gate
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
