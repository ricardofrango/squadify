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

/**
 * Removes a logged event (a mis-tap goal, card, or substitution) and re-broadcasts the recomputed
 * score. The client always confirms with the user before calling this -- see the live match console
 * notes in PROCESS_FLOWS.md ("Confirms only on Full time and event deletion").
 */
class DeleteMatchEventUseCase(
    private val matchEventRepository: MatchEventRepository,
    private val matchRepository: MatchRepository,
    private val liveMatchBus: LiveMatchBus,
    private val accessPolicy: AccessPolicy,
    private val clubMatchesBus: ClubMatchesBus,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        matchId: String,
        eventId: String
    ): Boolean = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        accessPolicy.requireManageLiveMatch(userId, match.scope, "Insufficient permissions to delete match events")

        match.requireLiveManager(userId)

        // Same rule as AddMatchEventUseCase: a FINISHED match's events (and the player stats frozen
        // from them) are final.
        if (match.status != MatchStatus.LIVE && match.status != MatchStatus.HALF_TIME) {
            throw ValidationException("Events can only be removed while the match is live")
        }

        val event = matchEventRepository.getEventById(eventId) ?: throw NotFoundException("Event not found")
        if (event.matchId != matchId) {
            throw ValidationException("Event does not belong to this match")
        }

        // A goal takes its assist with it (the assist points at the goal, so it goes first).
        val linkedAssists = matchEventRepository.getEventsByMatch(matchId).filter { it.relatedEventId == eventId }
        linkedAssists.forEach { matchEventRepository.deleteEvent(it.id) }

        val success = matchEventRepository.deleteEvent(eventId)
        if (success) {
            val score = matchEventRepository.getScoreByMatch(matchId)
            linkedAssists.forEach { liveMatchBus.publish(matchId, LiveMatchUpdate(score = score, deletedEventId = it.id)) }
            liveMatchBus.publish(matchId, LiveMatchUpdate(score = score, deletedEventId = eventId))
            clubMatchesBus.publish(match.clubId, ClubMatchesUpdate(score = score))
        }
        success
    }
}
