package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.ClubRepository
import com.sportssoftw.squadify.model.UpdateClubRequest

class UpdateClubUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String, request: UpdateClubRequest) =
        clubRepository.updateClub(clubId, request)
}
