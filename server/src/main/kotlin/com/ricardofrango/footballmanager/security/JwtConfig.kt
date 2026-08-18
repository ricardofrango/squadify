package com.ricardofrango.footballmanager.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

object JwtConfig {
    private const val SECRET = "my-super-secret-football-manager-key" // In production, use environment variables
    private const val ISSUER = "com.ricardofrango.footballmanager"
    private const val AUDIENCE = "football-manager-users"
    
    private const val ACCESS_TOKEN_VALIDITY_MS = 3_600_000 // 1 hour
    private const val REFRESH_TOKEN_VALIDITY_MS = 36_000_000 * 24 // 24 days

    private val algorithm = Algorithm.HMAC256(SECRET)

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .build()

    fun generateAccessToken(userId: String): String = generateToken(userId, ACCESS_TOKEN_VALIDITY_MS)
    
    fun generateRefreshToken(): String = UUID.randomUUID().toString()

    private fun generateToken(userId: String, validityMs: Int): String = JWT.create()
        .withSubject(userId)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withExpiresAt(Date(System.currentTimeMillis() + validityMs))
        .sign(algorithm)
        
    fun getRefreshTokenExpiration(): Long = System.currentTimeMillis() + REFRESH_TOKEN_VALIDITY_MS
}
