package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.domain.service.LiveFeedEvent
import com.sportssoftw.squadify.domain.service.keepConnected
import com.sportssoftw.squadify.model.LiveMatchUpdate
import kotlinx.coroutines.flow.Flow

/** A match's live updates, reconnecting whenever the socket drops (see [keepConnected]). */
class ObserveLiveMatchUseCase(
    private val matchRepository: MatchRepository
) {
    operator fun invoke(matchId: String): Flow<LiveFeedEvent<LiveMatchUpdate>> =
        keepConnected { matchRepository.observeLiveMatch(matchId) }
}
