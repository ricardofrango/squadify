package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.model.UpdateClubRequest

class UpdateClubUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String, request: UpdateClubRequest) =
        clubRepository.updateClub(clubId, request)
}
