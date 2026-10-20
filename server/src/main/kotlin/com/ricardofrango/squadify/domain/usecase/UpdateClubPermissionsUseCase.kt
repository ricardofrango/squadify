package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.UpdateClubPermissionsRequest
import com.ricardofrango.squadify.model.defaultSetting
import com.ricardofrango.squadify.model.isClubLevel

/**
 * Changes a club's configurable-permission thresholds. Deliberately fixed at `minClubRole =
 * ClubRole.COORDINATOR` -- per design, who may change these settings is never itself one of the
 * settings (see TECHNICAL_DECISIONS.md). Replaces the club's entire override set with exactly what the
 * client sends, so the client always submits the full form, not a partial patch.
 *
 * Club-level actions ([isClubLevel], e.g. disabling players) are the exception: their threshold is a
 * club role, and only a `CLUB_ADMIN` may change it -- otherwise a coordinator could undo an admin's
 * "admins only". A coordinator can still save the rest of the form as long as those stay as they are,
 * and a request that leaves a club-level action out keeps its current setting rather than resetting it.
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

            request.settings.forEach { setting ->
                if (setting.action.isClubLevel) {
                    if (setting.minTeamRole != null || setting.minClubRole !in CLUB_LEVEL_THRESHOLDS) {
                        throw ValidationException("${setting.action} takes admins and coordinators, or admins only")
                    }
                } else if (setting.minClubRole != null) {
                    throw ValidationException("${setting.action} takes an age-group role, not a club role")
                }
            }

            val current = clubPermissionRepository.getOverrides(clubId).associateBy { it.action }
            // Club-level actions left out of the request keep what they have.
            val kept = current.values.filter { it.action.isClubLevel && it.action !in actions }
            val settings = request.settings + kept

            val changesClubLevel = settings.filter { it.action.isClubLevel }.any { setting ->
                val before = current[setting.action] ?: setting.action.defaultSetting()
                before.minClubRole != setting.minClubRole
            }
            if (changesClubLevel) {
                checkUserPermissionUseCase(
                    userId = userId,
                    clubId = clubId,
                    minClubRole = ClubRole.CLUB_ADMIN,
                    errorMessage = "Only admins can change who disables and reactivates players"
                )
            }

            clubPermissionRepository.replaceOverrides(clubId, settings)
        }
}

// The two choices for a club-level action: admins and coordinators, or admins only.
private val CLUB_LEVEL_THRESHOLDS = setOf(ClubRole.COORDINATOR, ClubRole.CLUB_ADMIN)
