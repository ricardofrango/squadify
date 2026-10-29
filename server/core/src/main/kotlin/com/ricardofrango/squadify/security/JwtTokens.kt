package com.ricardofrango.squadify.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Sign-in tokens, shared by both servers (TECHNICAL_DECISIONS §72): a signed access token (JWT, the
 * user id as its subject) and an opaque refresh token. Each server has its own [secret] (its own
 * `JWT_SECRET`) and [audience], so a token from one product is never accepted by the other.
 */
open class JwtTokens(secret: String, audience: String) {
    // Expressed via kotlin.time.Duration so the values are unambiguous and can't silently overflow an
    // Int (the previous `36_000_000 * 24` literal was meant to be 24 days but actually computed to 10).
    // Was 1 hour -- too short in practice: a session opened at the start of a live match (which can run
    // well past an hour once warm-up, delays, and the post-match wrap-up are counted) would expire mid-
    // match, and the client only refreshes proactively at app start, not automatically on a 401. Bumped
    // to 48 hours as a first step; automatically retrying on 401 with a silent refresh is the follow-up.
    private val accessTokenValidityMs: Long = 48.hours.inWholeMilliseconds
    private val refreshTokenValidityMs: Long = 30.days.inWholeMilliseconds

    private val algorithm = Algorithm.HMAC256(secret)
    private val audience = audience

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer(ISSUER)
        .withAudience(audience)
        .build()

    fun generateAccessToken(userId: String): String = JWT.create()
        .withSubject(userId)
        .withIssuer(ISSUER)
        .withAudience(audience)
        .withExpiresAt(Date(System.currentTimeMillis() + accessTokenValidityMs))
        .sign(algorithm)

    fun generateRefreshToken(): String = UUID.randomUUID().toString()

    fun getRefreshTokenExpiration(): Long = System.currentTimeMillis() + refreshTokenValidityMs

    private companion object {
        const val ISSUER = "com.ricardofrango.squadify"
    }
}
