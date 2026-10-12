package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.TeamRole

// Who may do what with an age group's training (design 39m).

/** Create, edit, cancel and delete sessions (and plans): `MANAGE_TRAINING`. Throws when not allowed. */
internal suspend fun CheckUserPermissionUseCase.requireManageTraining(userId: String, ageGroup: AgeGroup) =
    this(
        userId = userId,
        clubId = ageGroup.clubId,
        minClubRole = ClubRole.COORDINATOR,
        configurablePermission = ConfigurablePermission.MANAGE_TRAINING,
        ageGroupId = ageGroup.id,
        errorMessage = "Only coordinators, or this age group's staff the club allows, can manage its training"
    )

/** Rate players: coordinators, and every coach or staff member of the age group. Throws when not allowed. */
internal suspend fun CheckUserPermissionUseCase.requireRateTraining(userId: String, ageGroup: AgeGroup) =
    this(
        userId = userId,
        clubId = ageGroup.clubId,
        minClubRole = ClubRole.COORDINATOR,
        minTeamRole = TeamRole.STAFF,
        ageGroupId = ageGroup.id,
        errorMessage = "Only this age group's staff can rate its training"
    )

internal suspend fun allowed(check: suspend () -> Unit): Boolean = try {
    check()
    true
} catch (e: ForbiddenException) {
    false
}
