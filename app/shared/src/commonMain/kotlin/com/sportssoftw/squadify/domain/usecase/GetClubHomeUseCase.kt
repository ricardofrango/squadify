package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.ClubRepository
import com.sportssoftw.squadify.model.ClubHome

class GetClubHomeUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String): ClubHome {
        return clubRepository.getClubHome(clubId)
    }
}
