package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.model.Club

class GetClubsUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(): List<Club> {
        return clubRepository.getClubs()
    }
}
