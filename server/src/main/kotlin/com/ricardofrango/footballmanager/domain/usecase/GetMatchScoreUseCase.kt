package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchEventRepository
import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.*

class GetMatchScoreUseCase(
    private val matchEventRepository: MatchEventRepository,
    private val matchRepository: MatchRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, matchId: String): MatchScore {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        // 1. Check permissions using metadata from match
        checkUserPermissionUseCase(userId, match.clubId, ClubRole.MEMBER)

        return matchEventRepository.getScoreByMatch(matchId)
    }
}
