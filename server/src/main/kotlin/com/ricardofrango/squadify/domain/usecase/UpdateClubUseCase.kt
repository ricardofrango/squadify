package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.UpdateClubRequest

class UpdateClubUseCase(
    private val clubRepository: ClubRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        request: UpdateClubRequest
    ): Boolean = transactionManager.runInTransaction {
        if (request.name?.isBlank() == true) {
            throw ValidationException("Club name cannot be blank")
        }

        val existingClub = clubRepository.getClubById(clubId)
            ?: throw NotFoundException("Club not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only club admins or coordinators can update club details"
        )

        val updatedClub = existingClub.copy(
            name = request.name ?: existingClub.name,
            shortName = request.shortName ?: existingClub.shortName,
            logoUrl = request.logoUrl ?: existingClub.logoUrl,
            email = request.email ?: existingClub.email,
            phone = request.phone ?: existingClub.phone,
            address = request.address ?: existingClub.address,
            stadiumLocation = request.stadiumLocation ?: existingClub.stadiumLocation,
            status = request.status ?: existingClub.status
        )

        clubRepository.updateClub(updatedClub)
    }
}
