package com.ricardofrango.squadify.security

import com.auth0.jwt.JWT
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks the token-lifetime values after the "Refresh token lifetime is miscalculated" fix in
 * "issues to fix.md" -- High -- correctness / data integrity. The old `36_000_000 * 24` literal was
 * meant to be days but resolved to ~10 days; refresh tokens are 30 days. Access tokens were bumped from
 * 1 hour to 48 hours -- a 1-hour session was expiring mid-live-match, and the client only refreshes
 * proactively at app start, not automatically on a 401 (see TECHNICAL_DECISIONS.md on session length).
 */
class JwtConfigTest {

    private val oneMinuteMs = 60_000L
    private val fortyEightHoursMs = 48L * 60 * 60 * 1000
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
    fun accessTokenExpiresFortyEightHoursOut() {
        val before = System.currentTimeMillis()
        val token = JwtConfig.generateAccessToken("user-123")
        val decoded = JWT.decode(token)
        val delta = decoded.expiresAt.time - before

        assertTrue(
            delta in (fortyEightHoursMs - oneMinuteMs)..(fortyEightHoursMs + oneMinuteMs),
            "access token should expire ~48 hours out ($fortyEightHoursMs ms), but delta was $delta ms"
        )
        assertEquals("user-123", decoded.subject)
    }
}
