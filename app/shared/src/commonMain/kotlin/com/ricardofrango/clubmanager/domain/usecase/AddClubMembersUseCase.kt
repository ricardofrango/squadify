package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.ClubRepository
import com.ricardofrango.clubmanager.model.AddClubMembersRequest

class AddClubMembersUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String, request: AddClubMembersRequest) =
        clubRepository.addClubMembers(clubId, request)
}
