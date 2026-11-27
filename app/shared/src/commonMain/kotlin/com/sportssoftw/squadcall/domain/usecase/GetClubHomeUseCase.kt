package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.model.ClubHome

class GetClubHomeUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String): ClubHome {
        return clubRepository.getClubHome(clubId)
    }
}
