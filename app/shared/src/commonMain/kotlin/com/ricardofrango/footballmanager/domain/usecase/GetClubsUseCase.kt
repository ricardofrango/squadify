package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.model.Club

class GetClubsUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(): List<Club> {
        return clubRepository.getClubs()
    }
}
