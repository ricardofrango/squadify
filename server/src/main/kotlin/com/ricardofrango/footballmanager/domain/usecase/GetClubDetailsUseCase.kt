package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.Club
import com.ricardofrango.footballmanager.model.ClubRole

class GetClubDetailsUseCase(
    private val clubRepository: ClubRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): Club = transactionManager.runInTransaction {
        // Everyone who is a member of the club (Member level 1) can see details
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.MEMBER,
            errorMessage = "You do not have access to this club"
        )

        clubRepository.getClubById(clubId) ?: throw NotFoundException("Club not found")
    }
}
