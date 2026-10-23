package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.data.database.ClubPermissionOverrides
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.model.ClubPermissionSetting
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.util.toUuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

class ExposedClubPermissionRepository : ClubPermissionRepository {

    override suspend fun getOverride(clubId: String, action: ConfigurablePermission): ClubPermissionSetting? {
        val clubUuid = clubId.toUuid()
        return ClubPermissionOverrides.selectAll()
            .where { (ClubPermissionOverrides.clubId eq clubUuid) and (ClubPermissionOverrides.permission eq action) }
            .map(::toSetting)
            .singleOrNull()
    }

    override suspend fun getOverrides(clubId: String): List<ClubPermissionSetting> =
        ClubPermissionOverrides.selectAll()
            .where { ClubPermissionOverrides.clubId eq clubId.toUuid() }
            .map(::toSetting)

    // Full replace, not a per-row upsert -- the caller (UpdateClubPermissionsUseCase) always sends the
    // complete desired state for every configurable action, so clearing and re-inserting is simpler and
    // just as correct as diffing row-by-row.
    override suspend fun replaceOverrides(clubId: String, settings: List<ClubPermissionSetting>) {
        val clubUuid = clubId.toUuid()
        ClubPermissionOverrides.deleteWhere { ClubPermissionOverrides.clubId eq clubUuid }
        if (settings.isEmpty()) return
        ClubPermissionOverrides.batchInsert(settings) { setting ->
            this[ClubPermissionOverrides.id] = Uuid.random()
            this[ClubPermissionOverrides.clubId] = clubUuid
            this[ClubPermissionOverrides.permission] = setting.action
            this[ClubPermissionOverrides.minAgeGroupRole] = setting.minAgeGroupRole
            this[ClubPermissionOverrides.minClubRole] = setting.minClubRole
        }
    }

    private fun toSetting(row: ResultRow) = ClubPermissionSetting(
        action = row[ClubPermissionOverrides.permission],
        minAgeGroupRole = row[ClubPermissionOverrides.minAgeGroupRole],
        minClubRole = row[ClubPermissionOverrides.minClubRole]
    )
}
