package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubMatchesUpdate
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.LiveMatchUpdate
import com.ricardofrango.squadify.model.MatchStatus

/**
 * Removes a logged event (a mis-tap goal, card, or substitution) and re-broadcasts the recomputed
 * score. The client always confirms with the user before calling this -- see the live match console
 * notes in PROCESS_FLOWS.md ("Confirms only on Full time and event deletion").
 */
class DeleteMatchEventUseCase(
    private val matchEventRepository: MatchEventRepository,
    private val matchRepository: MatchRepository,
    private val liveMatchBus: LiveMatchBus,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val clubMatchesBus: ClubMatchesBus,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        matchId: String,
        eventId: String
    ): Boolean = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minClubRole = ClubRole.COORDINATOR,
            configurablePermission = ConfigurablePermission.MANAGE_LIVE_MATCH,
            teamId = match.teamId,
            errorMessage = "Insufficient permissions to delete match events"
        )

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
