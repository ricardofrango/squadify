package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.model.OpponentList
import com.ricardofrango.squadify.model.Paging

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
