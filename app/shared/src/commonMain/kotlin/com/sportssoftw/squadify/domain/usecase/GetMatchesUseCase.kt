package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.model.MatchListStatus
import com.sportssoftw.squadify.model.MatchesPage

/** One page of the club Matches tab's [MatchListStatus] list, narrowed by season, age group and team on the server. */
class GetMatchesUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(
        clubId: String,
        status: MatchListStatus,
        seasonId: String?,
        ageGroupId: String?,
        teamId: String?,
        offset: Int
    ): MatchesPage = matchRepository.getMatchesPage(clubId, status, seasonId, ageGroupId, teamId, offset)
}
