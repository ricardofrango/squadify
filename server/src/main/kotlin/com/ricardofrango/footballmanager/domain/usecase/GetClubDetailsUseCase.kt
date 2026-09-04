package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.ClubDetailsResponse
import com.ricardofrango.footballmanager.model.ClubRole

class GetClubDetailsUseCase(
    private val clubRepository: ClubRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): ClubDetailsResponse = transactionManager.runInTransaction {
        // Everyone who is a member of the club (Member level 1) can see details
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.MEMBER,
            allowPlayerOrParentAccess = true,
            errorMessage = "You do not have access to this club"
        )

        val club = clubRepository.getClubById(clubId) ?: throw NotFoundException("Club not found")

        // Seasons can only be created by COORDINATOR+ (see CreateSeasonUseCase's identical check). Reuse
        // that same gate here -- as a non-throwing probe -- so the client can tell an admin/coordinator
        // "no seasons yet, create one" apart from a plain member's "no seasons yet".
        val canCreateSeason = try {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = clubId,
                minClubRole = ClubRole.COORDINATOR,
                errorMessage = "Only admins or coordinators can create seasons"
            )
            true
        } catch (e: ForbiddenException) {
            false
        }

        ClubDetailsResponse(club, canCreateSeason)
    }
}
