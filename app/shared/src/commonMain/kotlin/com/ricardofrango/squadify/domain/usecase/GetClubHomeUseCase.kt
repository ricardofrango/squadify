package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.model.ClubHome

class GetClubHomeUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String): ClubHome {
        return clubRepository.getClubHome(clubId)
    }
}
