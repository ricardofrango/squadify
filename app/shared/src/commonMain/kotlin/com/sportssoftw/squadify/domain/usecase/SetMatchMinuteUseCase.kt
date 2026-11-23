package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.MatchRepository

class SetMatchMinuteUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, minute: Int) = matchRepository.setMatchMinute(matchId, minute)
}
