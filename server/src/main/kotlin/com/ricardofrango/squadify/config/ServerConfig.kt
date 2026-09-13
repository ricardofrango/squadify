package com.ricardofrango.squadify.config

/**
 * The two environments the server can run in. Defaults to DEV so a plain local `./gradlew run` keeps
 * working exactly as before -- set `APP_ENV=production` (or `prod`) to opt into production behavior.
 *
 * NOTE: "production" here only gates the things covered by "issues to fix.md" -- Critical: security
 * (the JWT secret and the H2 web console). It does NOT yet switch the database itself to PostgreSQL;
 * that is a separate, larger tracked item (see REQUIREMENTS.md's "Production Database" checkbox and
 * TECHNICAL_DECISIONS.md §3) and is intentionally out of scope here.
 */
enum class AppEnvironment {
    DEV,
    PRODUCTION
}

/**
 * Centralized, environment-aware server configuration. Reads from real environment variables so
 * secrets never need to live in source -- see "issues to fix.md", Critical -- security.
 */
object ServerConfig {
    val environment: AppEnvironment = when (System.getenv("APP_ENV")?.trim()?.lowercase()) {
        "production", "prod" -> AppEnvironment.PRODUCTION
        else -> AppEnvironment.DEV
    }

    /**
     * The JWT signing secret. Must be set via the `JWT_SECRET` environment variable in production --
     * the server refuses to start rather than fall back to an insecure default. In dev, an explicit
     * (clearly-labeled) fallback keeps `./gradlew run` working out of the box.
     */
    val jwtSecret: String = System.getenv("JWT_SECRET")?.takeIf { it.isNotBlank() }
        ?: if (environment == AppEnvironment.PRODUCTION) {
            throw IllegalStateException(
                "JWT_SECRET environment variable must be set when APP_ENV=production. Refusing to start " +
                    "with an insecure default secret."
            )
        } else {
            "dev-only-insecure-secret-do-not-use-in-production"
        }

    /**
     * Startup seeding of a platform-admin ("superuser") account. Lets a freshly-wiped dev database
     * come back up with an admin already present, so there is no manual bootstrapping step after a
     * clean build. The seeding itself is idempotent (see `SeedPlatformAdminUseCase`): it only creates
     * the account when no user with [seedAdminEmail] exists yet.
     *
     * - `DEV`: enabled by default, with clearly-labelled fallback credentials so `./gradlew run` works
     *   out of the box. Override with `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD`.
     * - `PRODUCTION`: disabled unless `SEED_ADMIN_ENABLED=true` is set explicitly, and then both
     *   `SEED_ADMIN_EMAIL` and `SEED_ADMIN_PASSWORD` are required -- there is never a password
     *   fallback in production.
     */
    val seedAdminEnabled: Boolean = when (System.getenv("SEED_ADMIN_ENABLED")?.trim()?.lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> environment == AppEnvironment.DEV
    }

    val seedAdminEmail: String = System.getenv("SEED_ADMIN_EMAIL")?.takeIf { it.isNotBlank() }
        ?: if (environment == AppEnvironment.PRODUCTION) {
            if (seedAdminEnabled) {
                throw IllegalStateException(
                    "SEED_ADMIN_EMAIL environment variable must be set when SEED_ADMIN_ENABLED=true in production."
                )
            } else {
                ""
            }
        } else {
            "ricardo.short.frango@gmail.com"
        }

    val seedAdminPassword: String = System.getenv("SEED_ADMIN_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: if (environment == AppEnvironment.PRODUCTION) {
            if (seedAdminEnabled) {
                throw IllegalStateException(
                    "SEED_ADMIN_PASSWORD environment variable must be set when SEED_ADMIN_ENABLED=true in production."
                )
            } else {
                ""
            }
        } else {
            "123456789"
        }
}
