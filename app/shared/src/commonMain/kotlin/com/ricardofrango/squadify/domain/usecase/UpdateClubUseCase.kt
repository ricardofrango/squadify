package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.model.UpdateClubRequest

class UpdateClubUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String, request: UpdateClubRequest) =
        clubRepository.updateClub(clubId, request)
}
