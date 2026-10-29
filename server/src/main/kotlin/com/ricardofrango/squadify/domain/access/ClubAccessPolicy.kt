package com.ricardofrango.squadify.domain.access

import com.ricardofrango.squadify.domain.usecase.CheckUserPermissionUseCase
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission

/**
 * The clubs server's [AccessPolicy]: club roles, age-group staff roles and the club's configurable
 * permissions, through [CheckUserPermissionUseCase]. Each check is the exact call the game use cases
 * made before the policy existed, so behaviour is unchanged.
 */
class ClubAccessPolicy(private val checkUserPermission: CheckUserPermissionUseCase) : AccessPolicy {

    override suspend fun requireMember(userId: String, clubId: String) =
        checkUserPermission(userId = userId, clubId = clubId, minClubRole = ClubRole.MEMBER)

    // Players and parents may read (never write): the only opt-in to allowPlayerOrParentAccess here.
    override suspend fun requireViewGames(userId: String, scope: GameScope) = checkUserPermission(
        userId = userId,
        clubId = scope.clubId,
        minClubRole = ClubRole.MEMBER,
        minAgeGroupRole = AgeGroupRole.STAFF,
        teamId = scope.teamId,
        ageGroupId = scope.ageGroupId,
        allowPlayerOrParentAccess = true
    )

    override suspend fun requireManageMatch(userId: String, scope: GameScope, errorMessage: String) =
        configurable(userId, scope, ConfigurablePermission.MANAGE_MATCH, errorMessage)

    override suspend fun requireManageCallUps(userId: String, scope: GameScope, errorMessage: String) =
        configurable(userId, scope, ConfigurablePermission.MANAGE_CALL_UPS, errorMessage)

    override suspend fun requireManageLiveMatch(userId: String, scope: GameScope, errorMessage: String) =
        configurable(userId, scope, ConfigurablePermission.MANAGE_LIVE_MATCH, errorMessage)

    override suspend fun requireSeeRatings(userId: String, scope: GameScope, errorMessage: String) = checkUserPermission(
        userId = userId,
        clubId = scope.clubId,
        minClubRole = ClubRole.COORDINATOR,
        minAgeGroupRole = AgeGroupRole.STAFF,
        teamId = scope.teamId,
        ageGroupId = scope.ageGroupId,
        errorMessage = errorMessage
    )

    private suspend fun configurable(userId: String, scope: GameScope, permission: ConfigurablePermission, errorMessage: String) =
        checkUserPermission(
            userId = userId,
            clubId = scope.clubId,
            minClubRole = ClubRole.COORDINATOR,
            configurablePermission = permission,
            teamId = scope.teamId,
            ageGroupId = scope.ageGroupId,
            errorMessage = errorMessage
        )
}
