package com.ricardofrango.squadify.coach.config

/**
 * The coach server's settings, from its Railway service's environment (TECHNICAL_DECISIONS §72). Same
 * variable names as the clubs server, set on its own service: on staging and production
 * (`APP_ENV=staging` or `production`) the database host and password and the JWT secret must be set, or
 * it refuses to start; locally everything
 * defaults to docker-compose's Postgres and a database of its own, `squadify_coach_dev`.
 */
object CoachConfig {
    private val appEnv = System.getenv("APP_ENV")?.trim()?.lowercase()
    val production: Boolean = appEnv in setOf("production", "prod")

    /**
     * On Railway (staging or production): the database, its password and the secret must be given,
     * and the database is never created here. Locally neither, so the server runs with no setup.
     */
    val deployed: Boolean = production || appEnv == "staging"

    val port: Int = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 8090

    val databaseHost: String = env("DATABASE_HOST") ?: required("DATABASE_HOST") ?: "localhost"
    val databasePort: Int = System.getenv("DATABASE_PORT")?.trim()?.toIntOrNull() ?: 5432
    val databaseUser: String = env("DATABASE_USER") ?: "postgres"
    val databasePassword: String = env("DATABASE_PASSWORD") ?: required("DATABASE_PASSWORD") ?: "postgres"
    val databaseName: String = env("DATABASE_NAME") ?: if (production) "squadify_coach_production" else "squadify_coach_dev"

    /** Its own secret, never the clubs server's: a token from one product is refused by the other. */
    val jwtSecret: String = env("JWT_SECRET") ?: required("JWT_SECRET") ?: "dev-only-insecure-coach-secret-do-not-use-in-production"

    /**
     * Resend, for the account's emails (password reset and email change codes). Not required: without a
     * key the server logs and skips each email, as the clubs server does.
     */
    val resendApiKey: String = env("RESEND_API_KEY") ?: ""

    /** The sender: the clubs server's address with the name "Squadify Coach" (the developer, 2026-10-30). */
    val emailFromAddress: String = env("RESEND_FROM_ADDRESS") ?: "Squadify Coach <onboarding@resend.dev>"

    private fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }

    /** Null locally, so the caller's dev default applies; deployed, refuses to start. */
    private fun required(name: String): String? =
        if (deployed) throw IllegalStateException("$name environment variable must be set when APP_ENV=$appEnv.") else null
}
