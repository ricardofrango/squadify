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
import com.ricardofrango.squadify.model.elapsedMatchSeconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A manual correction to the running clock (the touchline coach nudging it to match the referee's
 * watch) -- persisted server-side and broadcast like every other match-timing change, unlike the
 * client-only "nudge on top of the derived value" this replaced (see TECHNICAL_DECISIONS.md's
 * "Live Match Clock" section): that version never reached the server at all, so neither the club
 * Matches list nor a second device watching the same match ever saw the correction.
 *
 * Shifts whichever of `kickoffAt`/`secondHalfStartedAt` is currently driving [elapsedMatchSeconds] so
 * that function evaluates to [minute] at "now" (or at the match's own `pausedAt`, if paused -- so a
 * paused match's frozen display can still be corrected without accidentally resuming it).
 */
class SetMatchMinuteUseCase(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val liveMatchBus: LiveMatchBus,
    private val clubMatchesBus: ClubMatchesBus,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, matchId: String, minute: Int): Boolean = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        accessPolicy.requireManageLiveMatch(userId, match.scope, "Only admins, coordinators, coaches, or team staff can set the match minute")

        if (match.status != MatchStatus.LIVE) {
            throw ValidationException("Can only set the minute of a match that is currently live")
        }
        if (minute < 0) {
            throw ValidationException("Minute cannot be negative")
        }

        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        // The instant elapsedMatchSeconds itself would use "as of" -- real now while running, or the
        // frozen pausedAt while paused, so the shift lands the correct target either way.
        val at = match.pausedAt ?: now
        val targetSeconds = minute * 60L
        val secondHalfStart = match.secondHalfStartedAt
        val newKickoffAt: Instant
        val newSecondHalfStartedAt: Instant?
        if (secondHalfStart != null) {
            val baseSeconds = (match.halfLengthMinutes ?: 0) * 60L
            newKickoffAt = match.kickoffAt ?: now
            newSecondHalfStartedAt = at - (targetSeconds - baseSeconds).seconds
        } else {
            newKickoffAt = at - targetSeconds.seconds
            newSecondHalfStartedAt = null
        }

        val adjusted = matchRepository.adjustMatchClock(matchId, newKickoffAt, newSecondHalfStartedAt)
        if (adjusted) {
            val updatedMatch = match.copy(kickoffAt = newKickoffAt, secondHalfStartedAt = newSecondHalfStartedAt)
            val score = matchEventRepository.getScoreByMatch(matchId)
            liveMatchBus.publish(matchId, LiveMatchUpdate(score = score, match = updatedMatch))
            clubMatchesBus.publish(match.clubId, ClubMatchesUpdate(match = updatedMatch))
        }
        adjusted
    }
}
