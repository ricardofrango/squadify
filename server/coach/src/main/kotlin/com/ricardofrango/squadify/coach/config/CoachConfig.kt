package com.ricardofrango.squadify.coach.config

import java.io.File

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

    /**
     * The coach's own Firebase project's service account (`squadify-for-coach`), as raw JSON, for its
     * pushes. Not required: without it each push is logged and skipped, as on the clubs server. Locally,
     * `firebase-coach-service-account.json` (gitignored) in the repo root or any folder above the server's.
     */
    val firebaseServiceAccountJson: String = env("FIREBASE_SERVICE_ACCOUNT_JSON")
        ?: generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "firebase-coach-service-account.json") }
            .firstOrNull { it.isFile }
            ?.readText()
            ?.takeIf { it.isNotBlank() }
        ?: ""

    /**
     * Images (TECHNICAL_DECISIONS §57, §72): the coach server's own Railway Storage Bucket (never the
     * clubs server's), its keys under `coach/`. The same variables as the clubs server, mapped on this
     * service from its bucket's reference variables (`STORAGE_BUCKET=${{Bucket.BUCKET}}` and so on). Without a bucket, images go
     * on disk under [localImageDir] locally, and deployed uploads are refused (a container's disk is
     * wiped on every deploy).
     */
    val storageBucket: String? = env("STORAGE_BUCKET")
    val storageEndpoint: String = env("STORAGE_ENDPOINT") ?: "https://storage.railway.app"
    val storageRegion: String = env("STORAGE_REGION") ?: "auto"
    val storageAccessKeyId: String = env("STORAGE_ACCESS_KEY_ID") ?: ""
    val storageSecretAccessKey: String = env("STORAGE_SECRET_ACCESS_KEY") ?: ""

    /** Where images are kept locally without a bucket (relative to where the server runs). */
    val localImageDir: String = env("LOCAL_IMAGE_DIR") ?: "uploads"

    /**
     * This server's own address (no trailing slash), for a push's picture (the space's logo, an
     * opponent's, a scorer's photo), which the phone downloads without signing in: `PUBLIC_BASE_URL`,
     * else Railway's `RAILWAY_PUBLIC_DOMAIN` over https, else locally this machine's LAN address (the
     * dev app's devHost), as on the clubs server.
     */
    val publicBaseUrl: String? = env("PUBLIC_BASE_URL")?.trim()?.trimEnd('/')
        ?: env("RAILWAY_PUBLIC_DOMAIN")?.trim()?.let { "https://$it" }
        ?: lanAddress()?.takeIf { !deployed }?.let { "http://$it:$port" }

    private fun lanAddress(): String? = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<java.net.Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    private fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }

    /** Null locally, so the caller's dev default applies; deployed, refuses to start. */
    private fun required(name: String): String? =
        if (deployed) throw IllegalStateException("$name environment variable must be set when APP_ENV=$appEnv.") else null
}
