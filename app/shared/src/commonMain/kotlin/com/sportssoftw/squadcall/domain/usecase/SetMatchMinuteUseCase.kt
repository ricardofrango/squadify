package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.MatchRepository

class SetMatchMinuteUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, minute: Int) = matchRepository.setMatchMinute(matchId, minute)
}
