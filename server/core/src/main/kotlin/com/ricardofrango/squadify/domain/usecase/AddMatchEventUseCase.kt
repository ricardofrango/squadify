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
import com.ricardofrango.squadify.model.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AddMatchEventUseCase(
    private val matchEventRepository: MatchEventRepository,
    private val matchRepository: MatchRepository,
    private val liveMatchBus: LiveMatchBus,
    private val accessPolicy: AccessPolicy,
    private val notifyMatchEventUseCase: NotifyMatchEventUseCase,
    private val clubMatchesBus: ClubMatchesBus,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        matchId: String,
        request: CreateMatchEventRequest
    ): MatchEvent {
        val savedEvent = transactionManager.runInTransaction {
            // 1. Verify match exists first to get context
            val match = matchRepository.getMatchById(matchId)
                ?: throw NotFoundException("Match not found")

            // 2. Single granular permission check using metadata from match
            accessPolicy.requireManageLiveMatch(userId, match.scope, "Insufficient permissions to add match events")

            // Events only exist while the match is being played. Once it's FINISHED its player stats
            // are frozen from these events (see UpdateMatchStatusUseCase), so they can't change after.
            if (match.status != MatchStatus.LIVE && match.status != MatchStatus.HALF_TIME) {
                throw ValidationException("Events can only be logged while the match is live")
            }

            // An assist rides on our own player's goal, by a different player who was called up.
            val assistPlayerId = request.assistPlayerId
            if (assistPlayerId != null) {
                if (request.type != EventType.GOAL || request.isOpponent || request.playerId == null) {
                    throw ValidationException("An assist can only be added to a goal by one of our players")
                }
                if (assistPlayerId == request.playerId) {
                    throw ValidationException("A player can't assist their own goal")
                }
                if (matchRepository.getCallUpByMatchAndPlayer(matchId, assistPlayerId) == null) {
                    throw ValidationException("The assisting player wasn't called up for this match")
                }
            }

            // 3. Create the event
            val event = MatchEvent(
                id = Uuid.random().toString(),
                matchId = matchId,
                type = request.type,
                minute = request.minute,
                playerId = request.playerId,
                opponentPlayerName = request.opponentPlayerName,
                isOpponent = request.isOpponent,
                createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
                // The half is fixed now, from the match state -- not inferred later from timestamps,
                // since secondHalfStartedAt is the clock's reference point and moves with pause/resume
                // and "Set minute". Logged during the break (HALF_TIME) counts as the first half.
                period = if (match.status == MatchStatus.LIVE && match.secondHalfStartedAt != null) 2 else 1
            )

            val savedEvent = matchEventRepository.addEvent(event)
            // Saved in the same transaction as the goal, at the same minute and half, pointing at it.
            val savedAssist = assistPlayerId?.let {
                matchEventRepository.addEvent(
                    event.copy(
                        id = Uuid.random().toString(),
                        type = EventType.ASSIST,
                        playerId = it,
                        relatedEventId = savedEvent.id
                    )
                )
            }

            // 4. Get updated score
            val score = matchEventRepository.getScoreByMatch(matchId)

            // 5. Broadcast via the Live Match Bus (the match's own console) and the club-wide Matches
            // list bus (just the score, not a full Match -- nothing else about the row changed).
            liveMatchBus.publish(matchId, LiveMatchUpdate(savedEvent, score))
            savedAssist?.let { liveMatchBus.publish(matchId, LiveMatchUpdate(it, score)) }
            clubMatchesBus.publish(match.clubId, ClubMatchesUpdate(score = score))

            savedEvent
        }

        // Sent after the transaction commits -- a failed/slow push must never roll back the event.
        if (savedEvent.type == EventType.GOAL || savedEvent.type == EventType.OWN_GOAL) {
            notifyMatchEventUseCase(matchId, MatchNotificationEvent.GOAL, savedEvent)
        }

        return savedEvent
    }
}
