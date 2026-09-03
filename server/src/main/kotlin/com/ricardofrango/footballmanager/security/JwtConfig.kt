package com.ricardofrango.footballmanager.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.ricardofrango.footballmanager.config.ServerConfig
import java.util.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

object JwtConfig {
    // Fixed: previously a hardcoded literal (anyone with repo read access could forge tokens for any
    // user). Now sourced from ServerConfig, which reads the JWT_SECRET environment variable and refuses
    // to start in production without it. See "issues to fix.md" -- Critical -- security.
    private val SECRET = ServerConfig.jwtSecret
    private const val ISSUER = "com.ricardofrango.footballmanager"
    private const val AUDIENCE = "football-manager-users"
    
    // Expressed via kotlin.time.Duration so the values are unambiguous and can't silently overflow an
    // Int (the previous `36_000_000 * 24` literal was meant to be 24 days but actually computed to 10).
    private val ACCESS_TOKEN_VALIDITY_MS: Long = 1.hours.inWholeMilliseconds
    private val REFRESH_TOKEN_VALIDITY_MS: Long = 30.days.inWholeMilliseconds

    private val algorithm = Algorithm.HMAC256(SECRET)

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .build()

    fun generateAccessToken(userId: String): String = generateToken(userId, ACCESS_TOKEN_VALIDITY_MS)
    
    fun generateRefreshToken(): String = UUID.randomUUID().toString()

    private fun generateToken(userId: String, validityMs: Long): String = JWT.create()
        .withSubject(userId)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withExpiresAt(Date(System.currentTimeMillis() + validityMs))
        .sign(algorithm)
        
    fun getRefreshTokenExpiration(): Long = System.currentTimeMillis() + REFRESH_TOKEN_VALIDITY_MS
}
