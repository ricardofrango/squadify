package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.ClubRepository

class GetClubMembersUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String) = clubRepository.getClubMembers(clubId)
}
