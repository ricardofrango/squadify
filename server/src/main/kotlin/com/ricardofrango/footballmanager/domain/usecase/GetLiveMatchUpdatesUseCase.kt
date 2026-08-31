package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.service.LiveMatchBus
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.LiveMatchUpdate
import kotlinx.coroutines.flow.Flow

class GetLiveMatchUpdatesUseCase(
    private val matchRepository: MatchRepository,
    private val liveMatchBus: LiveMatchBus,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, matchId: String): Flow<LiveMatchUpdate> {
        val match = matchRepository.getMatchById(matchId)
            ?: throw NotFoundException("Match not found")

        // 1. Authorize: Member of club can see live updates
        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minClubRole = ClubRole.MEMBER
        )

        // 2. Return the Reactive Stream
        return liveMatchBus.subscribe(matchId)
    }
}
