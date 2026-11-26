package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.service.LiveFeedEvent
import com.sportssoftw.squadcall.domain.service.keepConnected
import com.sportssoftw.squadcall.model.ClubMatchesUpdate
import kotlinx.coroutines.flow.Flow

/** The club's matches' live updates, reconnecting whenever the socket drops (see [keepConnected]). */
class ObserveClubMatchesUseCase(
    private val matchRepository: MatchRepository
) {
    operator fun invoke(clubId: String): Flow<LiveFeedEvent<ClubMatchesUpdate>> =
        keepConnected { matchRepository.observeClubMatches(clubId) }
}
