package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchEventRepository
import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.CreateMatchEventRequest
import com.ricardofrango.footballmanager.model.LiveMatchUpdate
import com.ricardofrango.footballmanager.model.MatchEvent
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.security.MatchSessionManager
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AddMatchEventUseCase(
    private val matchEventRepository: MatchEventRepository,
    private val matchRepository: MatchRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        matchId: String,
        request: CreateMatchEventRequest
    ): MatchEvent {
        // 1. Verify match exists first to get context
        val match = matchRepository.getMatchById(matchId)
            ?: throw ValidationException("Match not found")

        // 2. Single granular permission check using metadata from match
        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minRole = Role.COACH,
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

        // 5. Broadcast via WebSockets
        MatchSessionManager.broadcastUpdate(matchId, LiveMatchUpdate(savedEvent, score))

        return savedEvent
    }
}
