package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.model.UserStatus

class CheckUserIsAdminUseCase(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager
) {
    /**
     * Checks if a user is a platform admin currently able to act as one.
     *
     * @param userId The ID of the user performing the action.
     * @return true if the user is an active platform admin, false otherwise -- a suspended/inactive
     *   admin returns false here rather than throwing, same as a non-admin, so every non-throwing
     *   caller (e.g. GetClubsUseCase, deciding whether to show every club or just the caller's own)
     *   degrades gracefully instead of needing its own status check; callers that gate access on this
     *   (GetSuperAdminDashboardUseCase, ProvisionClubUseCase) already throw ForbiddenException when it
     *   returns false, so a suspended admin's still-valid token can no longer reach either.
     */
    suspend operator fun invoke(userId: String): Boolean = transactionManager.runInTransaction {
        val user = userRepository.findById(userId) ?: throw ForbiddenException("User not found")
        user.isPlatformAdmin && user.status == UserStatus.ACTIVE
    }
}
