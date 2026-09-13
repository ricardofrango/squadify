package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.model.AddClubMembersRequest

class AddClubMembersUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String, request: AddClubMembersRequest) =
        clubRepository.addClubMembers(clubId, request)
}
