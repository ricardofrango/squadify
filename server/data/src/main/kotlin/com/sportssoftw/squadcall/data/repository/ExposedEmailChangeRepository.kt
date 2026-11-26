package com.sportssoftw.squadcall.data.repository

import com.sportssoftw.squadcall.data.database.EmailChangeCodes
import com.sportssoftw.squadcall.domain.repository.EmailChangeCode
import com.sportssoftw.squadcall.domain.repository.EmailChangeRepository
import com.sportssoftw.squadcall.util.toUuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class ExposedEmailChangeRepository : EmailChangeRepository {

    override suspend fun findByUser(userId: String): EmailChangeCode? =
        EmailChangeCodes.selectAll()
            .where { EmailChangeCodes.userId eq userId.toUuid() }
            .map {
                EmailChangeCode(
                    userId = it[EmailChangeCodes.userId].toString(),
                    newEmail = it[EmailChangeCodes.newEmail],
                    codeHash = it[EmailChangeCodes.codeHash],
                    expiresAt = it[EmailChangeCodes.expiresAt],
                    attempts = it[EmailChangeCodes.attempts],
                    createdAt = it[EmailChangeCodes.createdAt]
                )
            }
            .singleOrNull()

    override suspend fun replace(code: EmailChangeCode) {
        delete(code.userId)
        EmailChangeCodes.insert {
            it[userId] = code.userId.toUuid()
            it[newEmail] = code.newEmail
            it[codeHash] = code.codeHash
            it[expiresAt] = code.expiresAt
            it[attempts] = code.attempts
            it[createdAt] = code.createdAt
        }
    }

    override suspend fun incrementAttempts(userId: String) {
        EmailChangeCodes.update({ EmailChangeCodes.userId eq userId.toUuid() }) {
            it[attempts] = attempts + 1
        }
    }

    override suspend fun delete(userId: String) {
        EmailChangeCodes.deleteWhere { EmailChangeCodes.userId eq userId.toUuid() }
    }
}
