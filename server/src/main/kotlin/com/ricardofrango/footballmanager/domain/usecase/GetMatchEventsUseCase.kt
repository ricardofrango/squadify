package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchEventRepository
import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.MatchEvent
import com.ricardofrango.footballmanager.model.Role

class GetMatchEventsUseCase(
    private val matchEventRepository: MatchEventRepository,
    private val matchRepository: MatchRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, matchId: String): List<MatchEvent> {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        // 1. Check permissions first (Fail fast)
        checkUserPermissionUseCase(userId, match.clubId, Role.PLAYER)

        return matchEventRepository.getEventsByMatch(matchId)
    }
}
