package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.model.PlayersPage

/** One page of the club's Players screen: the season, age-group and name filters run on the server. */
class GetClubPlayersUseCase(
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(clubId: String, seasonId: String?, ageGroupId: String?, query: String, offset: Int): PlayersPage =
        playerRepository.getPlayersPage(clubId, seasonId, ageGroupId, query, offset)
}
