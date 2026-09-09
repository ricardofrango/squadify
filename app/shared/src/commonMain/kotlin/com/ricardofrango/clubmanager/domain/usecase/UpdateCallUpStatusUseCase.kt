package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.MatchRepository
import com.ricardofrango.clubmanager.model.UpdateCallUpRequest

class UpdateCallUpStatusUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(callUpId: String, request: UpdateCallUpRequest) =
        matchRepository.updateCallUpStatus(callUpId, request)
}
