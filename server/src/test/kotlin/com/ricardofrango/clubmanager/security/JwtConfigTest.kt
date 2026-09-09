package com.ricardofrango.clubmanager.security

import com.auth0.jwt.JWT
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks the token-lifetime values after the "Refresh token lifetime is miscalculated" fix in
 * "issues to fix.md" -- High -- correctness / data integrity. The old `36_000_000 * 24` literal was
 * meant to be days but resolved to ~10 days; refresh tokens are now 30 days, access tokens 1 hour.
 */
class JwtConfigTest {

    private val oneMinuteMs = 60_000L
    private val oneHourMs = 60L * 60 * 1000
    private val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000

    @Test
    fun refreshTokenExpiresThirtyDaysOut() {
        val before = System.currentTimeMillis()
        val expiresAt = JwtConfig.getRefreshTokenExpiration()
        val delta = expiresAt - before

        assertTrue(
            delta in (thirtyDaysMs - oneMinuteMs)..(thirtyDaysMs + oneMinuteMs),
            "refresh token should expire ~30 days out ($thirtyDaysMs ms), but delta was $delta ms"
        )
    }

    @Test
    fun accessTokenExpiresOneHourOut() {
        val before = System.currentTimeMillis()
        val token = JwtConfig.generateAccessToken("user-123")
        val decoded = JWT.decode(token)
        val delta = decoded.expiresAt.time - before

        assertTrue(
            delta in (oneHourMs - oneMinuteMs)..(oneHourMs + oneMinuteMs),
            "access token should expire ~1 hour out ($oneHourMs ms), but delta was $delta ms"
        )
        assertEquals("user-123", decoded.subject)
    }
}
