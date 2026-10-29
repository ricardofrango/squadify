package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.data.database.FcmTokens
import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.util.toUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ExposedFcmTokenRepository : FcmTokenRepository {

    override suspend fun upsertToken(userId: String, token: String) {
        // onUpdate only reassigns user_id -- created_at stays as the token row's original creation
        // time even when re-registered under a different user, rather than becoming a "last seen" ish
        // value under a name that says otherwise.
        FcmTokens.upsert(
            FcmTokens.token,
            onUpdate = { statement -> statement[FcmTokens.userId] = userId.toUuid() }
        ) {
            it[id] = Uuid.random()
            it[FcmTokens.userId] = userId.toUuid()
            it[FcmTokens.token] = token
            it[createdAt] = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        }
    }

    override suspend fun deleteToken(userId: String, token: String) {
        FcmTokens.deleteWhere { (FcmTokens.userId eq userId.toUuid()) and (FcmTokens.token eq token) }
    }

    override suspend fun getTokensForUsers(userIds: List<String>): Map<String, List<String>> {
        if (userIds.isEmpty()) return emptyMap()
        return FcmTokens.selectAll()
            .where { FcmTokens.userId inList userIds.map(String::toUuid) }
            .map { it[FcmTokens.userId].toString() to it[FcmTokens.token] }
            .groupBy({ it.first }, { it.second })
    }
}
