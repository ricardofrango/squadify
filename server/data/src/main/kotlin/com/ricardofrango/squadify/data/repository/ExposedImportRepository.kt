package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.data.database.ExportGrants
import com.ricardofrango.squadify.data.database.ImportLinks
import com.ricardofrango.squadify.data.database.ImportRecords
import com.ricardofrango.squadify.domain.repository.ExportGrantRepository
import com.ricardofrango.squadify.domain.repository.ImportRepository
import com.ricardofrango.squadify.util.toUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant

class ExposedExportGrantRepository : ExportGrantRepository {

    override suspend fun create(tokenHash: String, userId: String, expiresAtEpochMillis: Long) {
        ExportGrants.insert {
            it[ExportGrants.tokenHash] = tokenHash
            it[ExportGrants.userId] = userId.toUuid()
            it[expiresAt] = Instant.fromEpochMilliseconds(expiresAtEpochMillis)
        }
    }

    override suspend fun userOf(tokenHash: String, nowEpochMillis: Long): String? {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        return ExportGrants.selectAll().where {
            (ExportGrants.tokenHash eq tokenHash) and ExportGrants.usedAt.isNull() and ExportGrants.expiresAt.greater(now)
        }.singleOrNull()?.get(ExportGrants.userId)?.toString()
    }

    override suspend fun redeem(tokenHash: String, nowEpochMillis: Long): String? {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        // Only a grant that's unused and not expired is taken: the update matches it once, so a second use finds nothing.
        val row = ExportGrants.selectAll().where {
            (ExportGrants.tokenHash eq tokenHash) and ExportGrants.usedAt.isNull() and ExportGrants.expiresAt.greater(now)
        }.singleOrNull() ?: return null
        val changed = ExportGrants.update({
            (ExportGrants.tokenHash eq tokenHash) and ExportGrants.usedAt.isNull()
        }) { it[usedAt] = now }
        return if (changed == 1) row[ExportGrants.userId].toString() else null
    }
}

class ExposedImportRepository : ImportRepository {

    override suspend fun findAgeGroup(targetClubId: String, sourceServer: String, sourceAgeGroupId: String): String? =
        ImportLinks.selectAll().where {
            (ImportLinks.targetClubId eq targetClubId.toUuid()) and
                (ImportLinks.sourceServer eq sourceServer) and
                (ImportLinks.sourceAgeGroupId eq sourceAgeGroupId.toUuid())
        }.singleOrNull()?.get(ImportLinks.targetAgeGroupId)?.toString()

    override suspend fun linkedAgeGroups(targetClubId: String, sourceServer: String): Set<String> =
        ImportLinks.selectAll().where {
            (ImportLinks.targetClubId eq targetClubId.toUuid()) and (ImportLinks.sourceServer eq sourceServer)
        }.map { it[ImportLinks.sourceAgeGroupId].toString() }.toSet()

    override suspend fun saveAgeGroupLink(targetClubId: String, sourceServer: String, sourceAgeGroupId: String, targetAgeGroupId: String) {
        ImportLinks.insert {
            it[ImportLinks.targetClubId] = targetClubId.toUuid()
            it[ImportLinks.sourceServer] = sourceServer
            it[ImportLinks.sourceAgeGroupId] = sourceAgeGroupId.toUuid()
            it[ImportLinks.targetAgeGroupId] = targetAgeGroupId.toUuid()
        }
    }

    override suspend fun saveRecord(targetClubId: String, sourceServer: String, sourceAgeGroupId: String, kind: String, sourceId: String, targetId: String) {
        ImportRecords.insert {
            it[ImportRecords.targetClubId] = targetClubId.toUuid()
            it[ImportRecords.sourceServer] = sourceServer
            it[ImportRecords.sourceAgeGroupId] = sourceAgeGroupId.toUuid()
            it[ImportRecords.kind] = kind
            it[ImportRecords.sourceId] = sourceId.toUuid()
            it[ImportRecords.targetId] = targetId.toUuid()
        }
    }

    override suspend fun forgetRecords(targetClubId: String, sourceServer: String, sourceAgeGroupId: String) {
        ImportRecords.deleteWhere {
            (ImportRecords.targetClubId eq targetClubId.toUuid()) and
                (ImportRecords.sourceServer eq sourceServer) and
                (ImportRecords.sourceAgeGroupId eq sourceAgeGroupId.toUuid())
        }
    }
}
