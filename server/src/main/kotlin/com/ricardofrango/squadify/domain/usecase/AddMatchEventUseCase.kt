package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AddMatchEventUseCase(
    private val matchEventRepository: MatchEventRepository,
    private val matchRepository: MatchRepository,
    private val liveMatchBus: LiveMatchBus,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        matchId: String,
        request: CreateMatchEventRequest
    ): MatchEvent = transactionManager.runInTransaction {
        // 1. Verify match exists first to get context
        val match = matchRepository.getMatchById(matchId)
            ?: throw ValidationException("Match not found")

        // 2. Single granular permission check using metadata from match
        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minTeamRole = TeamRole.COACH,
            teamId = match.teamId,
            errorMessage = "Insufficient permissions to add match events"
        )

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

        // 5. Broadcast via the Live Match Bus
        liveMatchBus.publish(matchId, LiveMatchUpdate(savedEvent, score))

        savedEvent
    }
}
