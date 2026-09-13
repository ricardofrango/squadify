package com.ricardofrango.squadify.config

/**
 * The two environments the server can run in. Defaults to DEV so a plain local `./gradlew run` keeps
 * working exactly as before -- set `APP_ENV=production` (or `prod`) to opt into production behavior.
 *
 * "Production" gates the things covered by "issues to fix.md" -- Critical: security (the JWT secret)
 * -- and, per §3 of TECHNICAL_DECISIONS.md, requires explicit PostgreSQL connection details (host +
 * password; see [ServerConfig.databaseHost]/[ServerConfig.databasePassword]) rather than falling back
 * to local dev defaults.
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
     * PostgreSQL connection details -- the only database engine now (H2 is gone). `databaseHost` and
     * `databasePassword` follow the same require-in-production pattern as [jwtSecret]: a sensible
     * "localhost" / dev credential default keeps `./gradlew run` working locally (against the Postgres
     * instance from `docker-compose.yml`), but the server refuses to start in production without both
     * set explicitly -- a silent "localhost" fallback in production would be a bug, not a convenience.
     * `databaseName` is the one field that *is* allowed a per-environment default rather than requiring
     * an explicit value, since dev and production pointing at different database names on the same or
     * different Postgres servers is the whole point (`squadify_dev` vs `squadify_production`).
     */
    val databaseHost: String = System.getenv("DATABASE_HOST")?.takeIf { it.isNotBlank() }
        ?: if (environment == AppEnvironment.PRODUCTION) {
            throw IllegalStateException(
                "DATABASE_HOST environment variable must be set when APP_ENV=production. Refusing to start " +
                    "against an implicit localhost database."
            )
        } else {
            "localhost"
        }

    val databasePort: Int = System.getenv("DATABASE_PORT")?.trim()?.toIntOrNull() ?: 5432

    val databaseUser: String = System.getenv("DATABASE_USER")?.takeIf { it.isNotBlank() } ?: "postgres"

    val databasePassword: String = System.getenv("DATABASE_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: if (environment == AppEnvironment.PRODUCTION) {
            throw IllegalStateException(
                "DATABASE_PASSWORD environment variable must be set when APP_ENV=production. Refusing to " +
                    "start with an insecure default database password."
            )
        } else {
            "postgres"
        }

    val databaseName: String = System.getenv("DATABASE_NAME")?.takeIf { it.isNotBlank() }
        ?: if (environment == AppEnvironment.PRODUCTION) "squadify_production" else "squadify_dev"

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
