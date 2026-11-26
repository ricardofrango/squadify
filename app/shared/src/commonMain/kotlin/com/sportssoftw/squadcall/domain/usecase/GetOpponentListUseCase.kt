package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.model.OpponentList
import com.sportssoftw.squadcall.model.Paging

class GetOpponentListUseCase(
    private val opponentRepository: OpponentRepository
) {
    /** The whole list (the form's duplicate-name check). */
    suspend operator fun invoke(clubId: String, ageGroupId: String? = null): OpponentList =
        opponentRepository.getOpponentList(clubId, ageGroupId)

    /** One page for the Opponents screen, searched on the server. */
    suspend fun page(clubId: String, ageGroupId: String?, query: String, offset: Int): OpponentList =
        opponentRepository.getOpponentList(clubId, ageGroupId, query, offset, limit = Paging.PAGE_SIZE)
}
