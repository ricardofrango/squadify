package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.model.UpdateCallUpRequest

class UpdateCallUpStatusUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(callUpId: String, request: UpdateCallUpRequest) =
        matchRepository.updateCallUpStatus(callUpId, request)
}
