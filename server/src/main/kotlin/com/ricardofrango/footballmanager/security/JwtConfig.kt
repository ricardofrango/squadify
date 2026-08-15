package com.ricardofrango.footballmanager.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

object JwtConfig {
    private const val SECRET = "my-super-secret-football-manager-key" // In production, use environment variables
    private const val ISSUER = "com.ricardofrango.footballmanager"
    private const val AUDIENCE = "football-manager-users"
    private const val VALIDITY_MS = 36_000_00 * 24 // 24 hours

    private val algorithm = Algorithm.HMAC256(SECRET)

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .build()

    fun generateToken(userId: String): String = JWT.create()
        .withSubject(userId)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withExpiresAt(Date(System.currentTimeMillis() + VALIDITY_MS))
        .sign(algorithm)
}
