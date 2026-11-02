package com.ricardofrango.squadify.coach.data

import com.ricardofrango.squadify.util.toUuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** A stored staff invite. [code] without the dash. */
data class StoredStaffInvite(
    val id: String,
    val code: String,
    val ageGroupId: String,
    val createdBy: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val acceptedBy: String?,
    // The address it was emailed to; null when shared by hand.
    val sentTo: String? = null
)

/** The coach server's staff invites. Called inside the use cases' transactions. */
interface StaffInviteRepository {
    suspend fun create(invite: StoredStaffInvite)
    /** How many codes [userId] has emailed since [since], for the daily limit. */
    suspend fun countEmailedSince(userId: String, since: Instant): Long
    suspend fun findByCode(code: String): StoredStaffInvite?
    /** Not used yet and not expired at [now], newest first. */
    suspend fun pending(ageGroupId: String, now: Instant): List<StoredStaffInvite>
    suspend fun markAccepted(id: String, userId: String, at: Instant): Boolean
    /** Deletes [code] if it belongs to [ageGroupId] and wasn't used; returns whether it did. */
    suspend fun revoke(ageGroupId: String, code: String): Boolean
}

class ExposedStaffInviteRepository : StaffInviteRepository {
    override suspend fun create(invite: StoredStaffInvite) {
        StaffInvites.insert {
            it[id] = invite.id.toUuid()
            it[code] = invite.code
            it[ageGroupId] = invite.ageGroupId.toUuid()
            it[createdBy] = invite.createdBy.toUuid()
            it[createdAt] = invite.createdAt
            it[expiresAt] = invite.expiresAt
            it[sentTo] = invite.sentTo
        }
    }

    override suspend fun countEmailedSince(userId: String, since: Instant): Long =
        StaffInvites.selectAll()
            .where { (StaffInvites.createdBy eq userId.toUuid()) and StaffInvites.sentTo.isNotNull() and (StaffInvites.createdAt greaterEq since) }
            .count()

    override suspend fun findByCode(code: String): StoredStaffInvite? =
        StaffInvites.selectAll().where { StaffInvites.code eq code }.map(::toInvite).singleOrNull()

    override suspend fun pending(ageGroupId: String, now: Instant): List<StoredStaffInvite> =
        StaffInvites.selectAll()
            .where { (StaffInvites.ageGroupId eq ageGroupId.toUuid()) and StaffInvites.acceptedBy.isNull() and (StaffInvites.expiresAt greater now) }
            .orderBy(StaffInvites.createdAt to SortOrder.DESC)
            .map(::toInvite)

    override suspend fun markAccepted(id: String, userId: String, at: Instant): Boolean =
        StaffInvites.update({ (StaffInvites.id eq id.toUuid()) and StaffInvites.acceptedBy.isNull() }) {
            it[acceptedBy] = userId.toUuid()
            it[acceptedAt] = at
        } > 0

    override suspend fun revoke(ageGroupId: String, code: String): Boolean =
        StaffInvites.deleteWhere {
            (StaffInvites.ageGroupId eq ageGroupId.toUuid()) and (StaffInvites.code eq code) and StaffInvites.acceptedBy.isNull()
        } > 0

    private fun toInvite(row: org.jetbrains.exposed.v1.core.ResultRow) = StoredStaffInvite(
        id = row[StaffInvites.id].toString(),
        code = row[StaffInvites.code],
        ageGroupId = row[StaffInvites.ageGroupId].toString(),
        createdBy = row[StaffInvites.createdBy].toString(),
        createdAt = row[StaffInvites.createdAt],
        expiresAt = row[StaffInvites.expiresAt],
        acceptedBy = row[StaffInvites.acceptedBy]?.toString(),
        sentTo = row[StaffInvites.sentTo]
    )
}

/** A fresh id for a new invite. */
fun newInviteId(): String = Uuid.random().toString()
