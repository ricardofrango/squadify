package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.UpdateClubPermissionsRequest

/**
 * Changes a club's configurable-permission thresholds. Deliberately fixed at `minClubRole =
 * ClubRole.COORDINATOR` -- per design, who may change these settings is never itself one of the
 * settings (see TECHNICAL_DECISIONS.md). Replaces the club's entire override set with exactly what the
 * client sends, so the client always submits the full 8-action form, not a partial patch.
 */
class UpdateClubPermissionsUseCase(
    private val clubPermissionRepository: ClubPermissionRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String, request: UpdateClubPermissionsRequest): Unit =
        transactionManager.runInTransaction {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = clubId,
                minClubRole = ClubRole.COORDINATOR,
                errorMessage = "Only admins or coordinators can change club permissions"
            )

            val actions = request.settings.map { it.action }
            if (actions.size != actions.toSet().size) {
                throw ValidationException("Duplicate permission action in request")
            }

            clubPermissionRepository.replaceOverrides(clubId, request.settings)
        }
}
