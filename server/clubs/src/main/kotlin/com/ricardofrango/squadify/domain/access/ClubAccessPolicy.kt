package com.ricardofrango.squadify.domain.access

import com.ricardofrango.squadify.domain.usecase.CheckUserPermissionUseCase
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission

/**
 * The clubs server's [AccessPolicy]: club roles, age-group staff roles and the club's configurable
 * permissions, through [CheckUserPermissionUseCase]. Each check is the exact call the game and
 * structure use cases made before the policy existed, so behaviour is unchanged.
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

    override suspend fun requireEditClub(userId: String, clubId: String, errorMessage: String) =
        checkUserPermission(userId = userId, clubId = clubId, minClubRole = ClubRole.COORDINATOR, errorMessage = errorMessage)

    override suspend fun requireViewClub(userId: String, clubId: String) =
        checkUserPermission(userId, clubId, minClubRole = ClubRole.MEMBER, allowPlayerOrParentAccess = true)

    override suspend fun visibleAgeGroupIds(userId: String, clubId: String): Set<String>? =
        checkUserPermission.visibleAgeGroupIds(userId, clubId)

    override suspend fun requireStaff(userId: String, scope: GameScope) = checkUserPermission(
        userId = userId,
        clubId = scope.clubId,
        minClubRole = ClubRole.MEMBER,
        minAgeGroupRole = AgeGroupRole.STAFF,
        teamId = scope.teamId,
        ageGroupId = scope.ageGroupId
    )

    override suspend fun requireViewMembers(userId: String, clubId: String) =
        checkUserPermission(userId = userId, clubId = clubId, minClubRole = ClubRole.COORDINATOR)

    override suspend fun requireCreateAgeGroups(userId: String, clubId: String, errorMessage: String) =
        checkUserPermission(userId = userId, clubId = clubId, minClubRole = ClubRole.COORDINATOR, errorMessage = errorMessage)

    // Editing an age group and adding its staff stay coordinator+ (not configurable, §35).
    override suspend fun requireEditAgeGroup(userId: String, scope: GameScope, errorMessage: String) =
        checkUserPermission(userId = userId, clubId = scope.clubId, minClubRole = ClubRole.COORDINATOR, errorMessage = errorMessage)

    override suspend fun requireManageStaff(userId: String, scope: GameScope, errorMessage: String) =
        checkUserPermission(userId = userId, clubId = scope.clubId, minClubRole = ClubRole.COORDINATOR, errorMessage = errorMessage)

    override suspend fun requireManageTeams(userId: String, scope: GameScope, errorMessage: String) =
        configurable(userId, scope, ConfigurablePermission.CREATE_TEAM, errorMessage)

    override suspend fun requireEditTeam(userId: String, scope: GameScope, errorMessage: String) = checkUserPermission(
        userId = userId,
        clubId = scope.clubId,
        minClubRole = ClubRole.COORDINATOR,
        minAgeGroupRole = AgeGroupRole.COACH,
        teamId = scope.teamId,
        ageGroupId = scope.ageGroupId,
        errorMessage = errorMessage
    )

    override suspend fun requireCreatePlayers(userId: String, scope: GameScope, errorMessage: String) =
        configurable(userId, scope, ConfigurablePermission.CREATE_PLAYERS, errorMessage)

    override suspend fun requireEditPlayer(userId: String, scope: GameScope, errorMessage: String) =
        configurable(userId, scope, ConfigurablePermission.EDIT_PLAYER, errorMessage)

    override suspend fun requireManagePool(userId: String, scope: GameScope, errorMessage: String) =
        configurable(userId, scope, ConfigurablePermission.ASSIGN_PLAYERS_TO_AGE_GROUP, errorMessage)

    // A coach of the age group with no club_members row still needs the list to schedule a match.
    override suspend fun requireViewOpponents(userId: String, scope: GameScope) = checkUserPermission(
        userId = userId,
        clubId = scope.clubId,
        minClubRole = ClubRole.MEMBER,
        minAgeGroupRole = AgeGroupRole.COACH,
        teamId = scope.teamId,
        ageGroupId = scope.ageGroupId
    )

    override suspend fun requireCreateOpponent(userId: String, scope: GameScope, errorMessage: String) =
        configurable(userId, scope, ConfigurablePermission.CREATE_OPPONENT, errorMessage)

    override suspend fun requireEditOpponent(userId: String, scope: GameScope, errorMessage: String) =
        configurable(userId, scope, ConfigurablePermission.EDIT_OPPONENT, errorMessage)

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
