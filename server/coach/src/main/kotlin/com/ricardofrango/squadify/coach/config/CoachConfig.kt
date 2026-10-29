package com.ricardofrango.squadify.coach.config

/**
 * The coach server's settings, from its Railway service's environment (TECHNICAL_DECISIONS §72). Same
 * variable names as the clubs server, set on its own service: in production (`APP_ENV=production`) the
 * database host and password and the JWT secret must be set, or it refuses to start; locally everything
 * defaults to docker-compose's Postgres and a database of its own, `squadify_coach_dev`.
 */
object CoachConfig {
    val production: Boolean = System.getenv("APP_ENV")?.trim()?.lowercase() in setOf("production", "prod")

    val port: Int = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 8090

    val databaseHost: String = env("DATABASE_HOST") ?: required("DATABASE_HOST") ?: "localhost"
    val databasePort: Int = System.getenv("DATABASE_PORT")?.trim()?.toIntOrNull() ?: 5432
    val databaseUser: String = env("DATABASE_USER") ?: "postgres"
    val databasePassword: String = env("DATABASE_PASSWORD") ?: required("DATABASE_PASSWORD") ?: "postgres"
    val databaseName: String = env("DATABASE_NAME") ?: if (production) "squadify_coach_production" else "squadify_coach_dev"

    /** Its own secret, never the clubs server's: a token from one product is refused by the other. */
    val jwtSecret: String = env("JWT_SECRET") ?: required("JWT_SECRET") ?: "dev-only-insecure-coach-secret-do-not-use-in-production"

    private fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }

    /** Null outside production, so the caller's dev default applies; in production, refuses to start. */
    private fun required(name: String): String? =
        if (production) throw IllegalStateException("$name environment variable must be set when APP_ENV=production.") else null
}
