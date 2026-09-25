package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository

class GetClubPermissionsUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String) = clubRepository.getClubPermissions(clubId)
}
