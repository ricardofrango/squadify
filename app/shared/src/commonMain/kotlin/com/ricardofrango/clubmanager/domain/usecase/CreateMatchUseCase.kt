package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.MatchRepository
import com.ricardofrango.clubmanager.model.CreateMatchRequest
import com.ricardofrango.clubmanager.model.Match

class CreateMatchUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(request: CreateMatchRequest): Match {
        return matchRepository.createMatch(request)
    }
}
