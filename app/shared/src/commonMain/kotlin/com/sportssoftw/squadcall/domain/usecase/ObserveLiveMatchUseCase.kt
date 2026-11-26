package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.service.LiveFeedEvent
import com.sportssoftw.squadcall.domain.service.keepConnected
import com.sportssoftw.squadcall.model.LiveMatchUpdate
import kotlinx.coroutines.flow.Flow

/** A match's live updates, reconnecting whenever the socket drops (see [keepConnected]). */
class ObserveLiveMatchUseCase(
    private val matchRepository: MatchRepository
) {
    operator fun invoke(matchId: String): Flow<LiveFeedEvent<LiveMatchUpdate>> =
        keepConnected { matchRepository.observeLiveMatch(matchId) }
}
