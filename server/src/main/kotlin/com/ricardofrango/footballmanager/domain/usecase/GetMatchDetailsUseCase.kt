package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.OpponentRepository
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.MatchDetails

class GetMatchDetailsUseCase(
    private val matchRepository: MatchRepository,
    private val opponentRepository: OpponentRepository
) {
    suspend operator fun invoke(matchId: String): MatchDetails {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")
        val opponent = opponentRepository.getOpponentById(match.opponentId) ?: throw NotFoundException("Opponent not found")
        val callUpsWithPlayer = matchRepository.getCallUpsForMatch(matchId)

        return MatchDetails(match, opponent, callUpsWithPlayer)
    }
}
