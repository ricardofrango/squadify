package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.model.ClubPermissionSetting
import com.ricardofrango.squadify.model.ClubPermissionsResponse
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.defaultMinTeamRole

/**
 * The effective setting for every [ConfigurablePermission], override-or-default -- always exactly one
 * entry per action, so the client never needs to know the defaults itself. Readable by any club member
 * (matches other "view club configuration" reads like `GetClubDetailsUseCase`); changing a setting is
 * the separate, coordinator+-only [UpdateClubPermissionsUseCase].
 */
class GetClubPermissionsUseCase(
    private val clubPermissionRepository: ClubPermissionRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, clubId: String): ClubPermissionsResponse {
        checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER)

        val overrides = clubPermissionRepository.getOverrides(clubId).associateBy { it.action }
        val settings = ConfigurablePermission.entries.map { action ->
            overrides[action] ?: ClubPermissionSetting(action, action.defaultMinTeamRole)
        }
        return ClubPermissionsResponse(settings)
    }
}
