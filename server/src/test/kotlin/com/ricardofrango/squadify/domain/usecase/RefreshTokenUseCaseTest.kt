package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.UnauthorizedException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.RefreshToken
import com.ricardofrango.squadify.model.User
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Covers the refresh-token flow bugs from "issues to fix.md" -- High -- correctness / data integrity:
 *  - "Refresh tokens never actually expire": `RefreshTokenUseCase` now checks the stored `expiresAt`
 *    and rejects an expired token exactly like an unknown one (also deleting the dead row).
 *  - "Refresh token lifetime is miscalculated": a rotated token must expire ~30 days out (was ~10).
 *
 * The expiry decision lives in the use case (a business rule), not in the repository query, so the
 * `FakeUserRepository` below is a dumb store — `findRefreshToken` returns whatever was saved, with no
 * time filtering of its own.
 */
class RefreshTokenUseCaseTest {

    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val repo = FakeUserRepository()
    private val useCase = RefreshTokenUseCase(repo, transactionManager)

    private fun now() = System.currentTimeMillis()
    private fun instantIn(ms: Long) = Instant.fromEpochMilliseconds(now() + ms)
    private val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000

    @Test
    fun validRefreshTokenIsRotatedForANewPair() = runBlocking {
        repo.seedRefreshToken("old-token", userId = "user-1", expiresAt = instantIn(thirtyDaysMs))

        val response = useCase("old-token")

        assertTrue(response.accessToken.isNotBlank(), "access token should be issued")
        assertTrue(response.refreshToken.isNotBlank(), "refresh token should be issued")
        assertTrue(response.refreshToken != "old-token", "refresh token must be rotated, not reused")
        assertNull(repo.expiryOf("old-token"), "the presented refresh token must be deleted")

        // The freshly issued refresh token must itself be stored against the same user.
        assertEquals("user-1", repo.findRefreshToken(response.refreshToken)?.userId)
        Unit
    }

    @Test
    fun expiredRefreshTokenIsRejectedAndPurged() = runBlocking {
        repo.seedRefreshToken("stale-token", userId = "user-1", expiresAt = instantIn(-60_000))

        assertFailsWith<UnauthorizedException> { useCase("stale-token") }

        // The use case deletes an expired token as it rejects it, so it can't be retried.
        assertNull(repo.expiryOf("stale-token"), "an expired token should be purged on a failed refresh")
        Unit
    }

    @Test
    fun unknownRefreshTokenIsRejected() = runBlocking {
        assertFailsWith<UnauthorizedException> { useCase("never-issued") }
        Unit
    }

    @Test
    fun rotatedRefreshTokenExpiresAboutThirtyDaysOut() = runBlocking {
        repo.seedRefreshToken("old-token", userId = "user-1", expiresAt = instantIn(thirtyDaysMs))

        val before = now()
        val response = useCase("old-token")
        val newExpiry = repo.expiryOf(response.refreshToken)?.toEpochMilliseconds()

        assertNotNull(newExpiry, "the new refresh token must be persisted with an expiry")
        val delta = newExpiry - before
        assertTrue(
            delta in (thirtyDaysMs - 60_000)..(thirtyDaysMs + 60_000),
            "new refresh token should expire ~30 days out, but delta was $delta ms"
        )
        Unit
    }
}

/**
 * Minimal in-memory [UserRepository]. Only the refresh-token methods carry real behaviour, and
 * `findRefreshToken` deliberately does no expiry filtering — that check belongs to the use case.
 * Everything else returns an empty/neutral value since [RefreshTokenUseCase] never touches it.
 */
private class FakeUserRepository : UserRepository {

    private val refreshTokens = mutableMapOf<String, Entry>()
    private data class Entry(val userId: String, val expiresAt: Instant)

    fun seedRefreshToken(token: String, userId: String, expiresAt: Instant) {
        refreshTokens[token] = Entry(userId, expiresAt)
    }

    fun expiryOf(token: String): Instant? = refreshTokens[token]?.expiresAt

    override suspend fun saveRefreshToken(userId: String, token: String, expiresAt: Instant) {
        refreshTokens[token] = Entry(userId, expiresAt)
    }

    override suspend fun findRefreshToken(token: String): RefreshToken? =
        refreshTokens[token]?.let { RefreshToken(userId = it.userId, expiresAt = it.expiresAt) }

    override suspend fun deleteRefreshToken(token: String) {
        refreshTokens.remove(token)
    }

    override suspend fun deleteRefreshTokensForUser(userId: String) {
        refreshTokens.entries.removeIf { it.value.userId == userId }
    }

    override suspend fun deleteRefreshTokensForUserExcept(userId: String, keepToken: String?) {
        refreshTokens.entries.removeIf { it.value.userId == userId && it.key != keepToken }
    }

    override suspend fun updateEmail(userId: String, email: String) = error("not used")
    override suspend fun incrementPasswordCheckFailures(userId: String) = error("not used")
    override suspend fun resetPasswordCheckFailures(userId: String) = error("not used")

    override suspend fun findById(id: String): User? = null
    override suspend fun findByEmail(email: String): User? = null
    override suspend fun findByEmails(emails: List<String>): List<User> = emptyList()
    override suspend fun findPasswordHashByEmail(email: String): String? = null
    override suspend fun createUser(user: User, passwordHash: String): User = user
    override suspend fun updateProfile(userId: String, firstName: String, lastName: String, phone: String?): Boolean = false
    override suspend fun updatePasswordHash(userId: String, passwordHash: String): Boolean = false
    override suspend fun getUserRoleInClub(userId: String, clubId: String): ClubRole? = null
    override suspend fun getUserRolesInClub(userIds: List<String>, clubId: String): Map<String, ClubRole> = emptyMap()
    override suspend fun countAllUsers(): Long = 0
}
