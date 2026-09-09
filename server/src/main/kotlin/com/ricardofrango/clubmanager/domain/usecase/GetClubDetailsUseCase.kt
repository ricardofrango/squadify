package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.ClubRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.ForbiddenException
import com.ricardofrango.clubmanager.exception.NotFoundException
import com.ricardofrango.clubmanager.model.ClubDetailsResponse
import com.ricardofrango.clubmanager.model.ClubRole

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

        // Seasons and age groups are both created by COORDINATOR+ (see CreateSeasonUseCase and
        // CreateAgeGroupsUseCase -- identical gate in both). One non-throwing probe covers both fields
        // rather than repeating the same permission check twice; it's still two separate response
        // fields so either could diverge later without an API shape change. Lets the client tell an
        // admin/coordinator "nothing here yet, create one" apart from a plain member's "nothing here yet".
        val canEditClub = try {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = clubId,
                minClubRole = ClubRole.COORDINATOR,
                errorMessage = "Only admins or coordinators can manage seasons and age groups"
            )
            true
        } catch (e: ForbiddenException) {
            false
        }

        ClubDetailsResponse(club, canEditClub)
    }
}
