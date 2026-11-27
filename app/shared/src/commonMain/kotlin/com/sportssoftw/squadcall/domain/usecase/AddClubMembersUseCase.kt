package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.model.AddClubMembersRequest

class AddClubMembersUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String, request: AddClubMembersRequest) =
        clubRepository.addClubMembers(clubId, request)
}
