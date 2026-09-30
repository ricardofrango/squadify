package com.ricardofrango.squadify.domain.usecase

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
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
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
            checkUserPermissionUseCase(
                userId = userId,
                clubId = match.clubId,
                minClubRole = ClubRole.COORDINATOR,
                configurablePermission = ConfigurablePermission.MANAGE_LIVE_MATCH,
                teamId = match.teamId,
                errorMessage = "Insufficient permissions to add match events"
            )

            // Events only exist while the match is being played. Once it's FINISHED its player stats
            // are frozen from these events (see UpdateMatchStatusUseCase), so they can't change after.
            if (match.status != MatchStatus.LIVE && match.status != MatchStatus.HALF_TIME) {
                throw ValidationException("Events can only be logged while the match is live")
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
                createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            )

            val savedEvent = matchEventRepository.addEvent(event)

            // 4. Get updated score
            val score = matchEventRepository.getScoreByMatch(matchId)

            // 5. Broadcast via the Live Match Bus (the match's own console) and the club-wide Matches
            // list bus (just the score, not a full Match -- nothing else about the row changed).
            liveMatchBus.publish(matchId, LiveMatchUpdate(savedEvent, score))
            clubMatchesBus.publish(match.clubId, ClubMatchesUpdate(score = score))

            savedEvent
        }

        // Sent after the transaction commits -- a failed/slow push must never roll back the event.
        if (savedEvent.type == EventType.GOAL || savedEvent.type == EventType.OWN_GOAL) {
            notifyMatchEventUseCase(matchId, MatchNotificationEvent.GOAL)
        }

        return savedEvent
    }
}
