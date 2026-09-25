package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.model.UpdateClubPermissionsRequest

class UpdateClubPermissionsUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String, request: UpdateClubPermissionsRequest) =
        clubRepository.updateClubPermissions(clubId, request)
}
