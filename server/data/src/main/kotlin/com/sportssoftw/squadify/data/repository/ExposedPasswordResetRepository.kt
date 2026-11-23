package com.sportssoftw.squadify.data.repository

import com.sportssoftw.squadify.data.database.PasswordResetCodes
import com.sportssoftw.squadify.domain.repository.PasswordResetCode
import com.sportssoftw.squadify.domain.repository.PasswordResetRepository
import com.sportssoftw.squadify.util.toUuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class ExposedPasswordResetRepository : PasswordResetRepository {

    override suspend fun findByUser(userId: String): PasswordResetCode? =
        PasswordResetCodes.selectAll()
            .where { PasswordResetCodes.userId eq userId.toUuid() }
            .map {
                PasswordResetCode(
                    userId = it[PasswordResetCodes.userId].toString(),
                    codeHash = it[PasswordResetCodes.codeHash],
                    expiresAt = it[PasswordResetCodes.expiresAt],
                    attempts = it[PasswordResetCodes.attempts],
                    createdAt = it[PasswordResetCodes.createdAt]
                )
            }
            .singleOrNull()

    override suspend fun replace(code: PasswordResetCode) {
        delete(code.userId)
        PasswordResetCodes.insert {
            it[userId] = code.userId.toUuid()
            it[codeHash] = code.codeHash
            it[expiresAt] = code.expiresAt
            it[attempts] = code.attempts
            it[createdAt] = code.createdAt
        }
    }

    override suspend fun incrementAttempts(userId: String) {
        PasswordResetCodes.update({ PasswordResetCodes.userId eq userId.toUuid() }) {
            it[attempts] = attempts + 1
        }
    }

    override suspend fun delete(userId: String) {
        PasswordResetCodes.deleteWhere { PasswordResetCodes.userId eq userId.toUuid() }
    }
}
