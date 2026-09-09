package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.ClubRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.NotFoundException
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.UpdateClubRequest

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
            status = request.status ?: existingClub.status
        )

        clubRepository.updateClub(updatedClub)
    }
}
