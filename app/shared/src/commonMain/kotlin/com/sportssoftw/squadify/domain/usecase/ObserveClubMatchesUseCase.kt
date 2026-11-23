package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.domain.service.LiveFeedEvent
import com.sportssoftw.squadify.domain.service.keepConnected
import com.sportssoftw.squadify.model.ClubMatchesUpdate
import kotlinx.coroutines.flow.Flow

/** The club's matches' live updates, reconnecting whenever the socket drops (see [keepConnected]). */
class ObserveClubMatchesUseCase(
    private val matchRepository: MatchRepository
) {
    operator fun invoke(clubId: String): Flow<LiveFeedEvent<ClubMatchesUpdate>> =
        keepConnected { matchRepository.observeClubMatches(clubId) }
}
