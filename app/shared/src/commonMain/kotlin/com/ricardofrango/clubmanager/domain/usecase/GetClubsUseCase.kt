package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.ClubRepository
import com.ricardofrango.clubmanager.model.Club

class GetClubsUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(): List<Club> {
        return clubRepository.getClubs()
    }
}
