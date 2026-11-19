package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.ClubRepository
import com.sportssoftw.squadify.model.UpdateClubPermissionsRequest

class UpdateClubPermissionsUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String, request: UpdateClubPermissionsRequest) =
        clubRepository.updateClubPermissions(clubId, request)
}
