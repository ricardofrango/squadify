package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.UpdateClubMemberRoleRequest

class UpdateClubMemberRoleUseCase(
    private val clubRepository: ClubRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        targetUserId: String,
        request: UpdateClubMemberRoleRequest
    ): Boolean = transactionManager.runInTransaction {
        // 1. Check permissions (Only CLUB_ADMIN can manage user roles at the club level)
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only club admins or coordinators can manage user roles at the club level"
        )

        // 2. Update role in the club_members table
        clubRepository.updateMemberRole(clubId, targetUserId, request.role)
    }
}
