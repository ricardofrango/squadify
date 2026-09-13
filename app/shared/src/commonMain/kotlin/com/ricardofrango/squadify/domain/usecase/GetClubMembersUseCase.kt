package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository

class GetClubMembersUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String) = clubRepository.getClubMembers(clubId)
}
