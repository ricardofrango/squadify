package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.ClubRepository
import com.sportssoftw.squadify.model.AddClubMembersRequest

class AddClubMembersUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String, request: AddClubMembersRequest) =
        clubRepository.addClubMembers(clubId, request)
}
