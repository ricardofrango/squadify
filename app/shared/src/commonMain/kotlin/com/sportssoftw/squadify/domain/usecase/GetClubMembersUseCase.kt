package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.ClubRepository

class GetClubMembersUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String) = clubRepository.getClubMembers(clubId)
}
