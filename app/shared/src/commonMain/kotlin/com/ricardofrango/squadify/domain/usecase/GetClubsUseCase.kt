package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.model.Club

class GetClubsUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(): List<Club> {
        return clubRepository.getClubs()
    }
}
