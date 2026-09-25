package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.ClubPermissionSetting
import com.ricardofrango.squadify.model.ConfigurablePermission

interface ClubPermissionRepository {
    /** Null when no override exists for this (club, action) pair -- caller should fall back to the
     * action's [com.ricardofrango.squadify.model.defaultMinTeamRole]. */
    suspend fun getOverride(clubId: String, action: ConfigurablePermission): ClubPermissionSetting?

    /** Only the explicit overrides a club actually has -- never padded out with defaults. */
    suspend fun getOverrides(clubId: String): List<ClubPermissionSetting>

    /** Replaces the club's entire set of overrides with exactly [settings]. */
    suspend fun replaceOverrides(clubId: String, settings: List<ClubPermissionSetting>)
}
