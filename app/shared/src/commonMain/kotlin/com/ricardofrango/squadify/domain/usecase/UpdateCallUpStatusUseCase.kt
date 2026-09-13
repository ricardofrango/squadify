package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.model.UpdateCallUpRequest

class UpdateCallUpStatusUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(callUpId: String, request: UpdateCallUpRequest) =
        matchRepository.updateCallUpStatus(callUpId, request)
}
