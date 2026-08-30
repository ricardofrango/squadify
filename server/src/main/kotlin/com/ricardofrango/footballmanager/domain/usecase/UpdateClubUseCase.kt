package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.UpdateClubRequest

class UpdateClubUseCase(
    private val clubRepository: ClubRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        request: UpdateClubRequest
    ): Boolean {
        val existingClub = clubRepository.getClubById(clubId)
            ?: throw NotFoundException("Club not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.CLUB_ADMIN,
            errorMessage = "Only club admins can update club details"
        )

        val updatedClub = existingClub.copy(
            name = request.name ?: existingClub.name,
            shortName = request.shortName ?: existingClub.shortName,
            logoUrl = request.logoUrl ?: existingClub.logoUrl,
            email = request.email ?: existingClub.email,
            phone = request.phone ?: existingClub.phone,
            address = request.address ?: existingClub.address,
            status = request.status ?: existingClub.status
        )

        return clubRepository.updateClub(updatedClub)
    }
}
