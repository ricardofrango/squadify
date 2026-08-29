package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchEventRepository
import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.*
import com.ricardofrango.footballmanager.security.MatchSessionManager
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AddMatchEventUseCase(
    private val matchEventRepository: MatchEventRepository,
    private val matchRepository: MatchRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, matchId: String, request: CreateMatchEventRequest): MatchEvent {
        // 1. Verify match exists
        val match = matchRepository.getMatchById(matchId)
            ?: throw ValidationException("Match not found")

        // 2. Check permissions (Admin/Coordinator or Team Coach)
        val userRole = userRepository.getUserRoleInClub(userId, match.clubId)
        // Note: In a real app, we'd check if this specific user is assigned as coach to this match's team.
        // For MVP, we allow any club COACH.
        if (userRole != Role.CLUB_ADMIN && userRole != Role.COORDINATOR && userRole != Role.COACH) {
            val user = userRepository.findById(userId)
            if (user?.isPlatformAdmin != true) {
                throw ForbiddenException("Insufficient permissions to add match events")
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
