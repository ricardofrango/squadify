package com.ricardofrango.squadify.config

import java.io.File

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

    /**
     * Resend API key for transactional email (club/age-group association notifications -- see
     * EmailService). Deliberately NOT required-in-production like [jwtSecret]/[databasePassword]:
     * email is a best-effort notification, not a security-critical value, so a missing key degrades
     * to EmailService logging and skipping the send rather than refusing to start the whole server.
     */
    val resendApiKey: String = System.getenv("RESEND_API_KEY")?.takeIf { it.isNotBlank() } ?: ""

    /**
     * The "from" address used for all outgoing email. Resend only allows sending from
     * `onboarding@resend.dev` (and only to the Resend account's own verified email) until a real
     * sending domain is verified -- see TECHNICAL_DECISIONS.md's EmailService section.
     */
    val emailFromAddress: String = System.getenv("RESEND_FROM_ADDRESS")?.takeIf { it.isNotBlank() }
        ?: "Squadify <onboarding@resend.dev>"

    /**
     * Image storage (club logos). On Railway, a Storage Bucket (S3-compatible): map the bucket's
     * reference variables onto these on the server service, e.g. `STORAGE_BUCKET=${{Bucket.BUCKET}}`,
     * `STORAGE_ENDPOINT=${{Bucket.ENDPOINT}}`, `STORAGE_REGION=${{Bucket.REGION}}`,
     * `STORAGE_ACCESS_KEY_ID=${{Bucket.ACCESS_KEY_ID}}`, `STORAGE_SECRET_ACCESS_KEY=${{Bucket.SECRET_ACCESS_KEY}}`.
     * Without a bucket, dev keeps images on disk under [localImageDir]; production refuses uploads
     * instead (a Railway container's disk is wiped on every deploy). See TECHNICAL_DECISIONS.md §57.
     */
    val storageBucket: String? = System.getenv("STORAGE_BUCKET")?.takeIf { it.isNotBlank() }
    val storageEndpoint: String = System.getenv("STORAGE_ENDPOINT")?.takeIf { it.isNotBlank() } ?: "https://storage.railway.app"
    val storageRegion: String = System.getenv("STORAGE_REGION")?.takeIf { it.isNotBlank() } ?: "auto"
    val storageAccessKeyId: String = System.getenv("STORAGE_ACCESS_KEY_ID")?.takeIf { it.isNotBlank() } ?: ""
    val storageSecretAccessKey: String = System.getenv("STORAGE_SECRET_ACCESS_KEY")?.takeIf { it.isNotBlank() } ?: ""

    /** Where [com.ricardofrango.squadify.data.service.LocalImageStorage] keeps images in dev (relative to `server/` under `./gradlew :server:run`). */
    val localImageDir: String = System.getenv("LOCAL_IMAGE_DIR")?.takeIf { it.isNotBlank() } ?: "uploads"

    /**
     * Base URL of the deployed web client, linked from invite/registration emails. Placeholder until
     * the web client is actually deployed -- see ISSUES TO FIX.md's "Client config points nowhere
     * real" item for the same pattern on the client side.
     */
    val webAppBaseUrl: String = System.getenv("WEBAPP_BASE_URL")?.takeIf { it.isNotBlank() }
        ?: "http://localhost:8081"

    /**
     * A Firebase service-account key (the raw JSON content, not a file path -- there's no reliable
     * place to mount a file on Railway, which is why the env var takes priority) used by
     * [com.ricardofrango.squadify.data.service.FirebaseAdminPushNotificationService] to send push
     * notifications through the same Firebase project (squadify-958d3) already registered for Android
     * -- see TECHNICAL_DECISIONS.md's push-notifications section. Same best-effort posture as
     * [resendApiKey]: push is a notification, not a security-critical value, so a missing/blank
     * credential degrades to that service logging and skipping the send rather than refusing to start.
     *
     * Local dev falls back to reading `firebase-service-account.json` at the repo root (gitignored,
     * never committed -- same file/pattern `app/androidApp/build.gradle.kts` already uses for App
     * Distribution's `keystoreProperties`) when the env var isn't set, so a plain `./gradlew :server:run`
     * picks it up automatically instead of requiring it exported in every shell. Checked at two
     * candidate paths, not just one: Gradle's `application`/`test` tasks default their JVM's working
     * directory to the `server/` module folder, not the repo root, even when `./gradlew` itself was
     * invoked from the root -- confirmed empirically (`File(".").absolutePath` prints `.../server`
     * under `:server:run`/`:server:test`), so a bare repo-root-relative path silently never matches
     * under the one launch method this is actually for. `../firebase-service-account.json` is what
     * resolves there; the bare repo-root-relative form stays as a second candidate for any other way
     * this might get invoked (e.g. running a packaged jar directly from the repo root).
     */
    val firebaseServiceAccountJson: String = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON")?.takeIf { it.isNotBlank() }
        ?: listOf("../firebase-service-account.json", "firebase-service-account.json")
            .asSequence()
            .map { File(it) }
            .firstOrNull { it.isFile }
            ?.readText()
            ?.takeIf { it.isNotBlank() }
        ?: ""
}
