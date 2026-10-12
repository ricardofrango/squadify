package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.model.OpponentList

class GetOpponentListUseCase(
    private val opponentRepository: OpponentRepository
) {
    suspend operator fun invoke(clubId: String, ageGroupId: String? = null): OpponentList =
        opponentRepository.getOpponentList(clubId, ageGroupId)
}
