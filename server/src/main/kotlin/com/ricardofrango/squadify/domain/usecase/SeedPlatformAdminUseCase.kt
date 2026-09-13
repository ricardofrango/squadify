package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Ensures a platform-admin ("superuser") account exists. Run once at server startup so a freshly
 * created/wiped dev database is immediately usable without any manual bootstrapping.
 *
 * Idempotent: if a user with [adminEmail] already exists the use case does nothing (it never touches
 * an existing account, so it can't silently escalate a normal user that happens to share the email).
 * A normal restart against an intact database is therefore a no-op; deleting the database file makes
 * the next startup recreate the admin.
 *
 * Configuration ([enabled], [adminEmail], [adminPassword]) is resolved from [ServerConfig][
 * com.ricardofrango.squadify.config.ServerConfig] and injected in `AppModule`, keeping this
 * use case free of environment lookups and trivially testable.
 */
class SeedPlatformAdminUseCase(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager,
    private val enabled: Boolean,
    private val adminEmail: String,
    private val adminPassword: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend operator fun invoke() = transactionManager.runInTransaction {
        if (!enabled) {
            logger.info("Platform-admin seeding is disabled; skipping.")
            return@runInTransaction
        }
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            logger.warn("Platform-admin seeding is enabled but email/password are not configured; skipping.")
            return@runInTransaction
        }

        if (userRepository.findByEmail(adminEmail) != null) {
            logger.info("Platform admin '{}' already exists; nothing to seed.", adminEmail)
            return@runInTransaction
        }

        val admin = User(
            id = Uuid.random().toString(),
            email = adminEmail,
            firstName = SEED_ADMIN_FIRST_NAME,
            lastName = SEED_ADMIN_LAST_NAME,
            status = UserStatus.ACTIVE,
            isPlatformAdmin = true,
            createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
        )
        userRepository.createUser(admin, BCrypt.hashpw(adminPassword, BCrypt.gensalt()))
        logger.info("Seeded platform admin '{}'.", adminEmail)
    }

    private companion object {
        const val SEED_ADMIN_FIRST_NAME = "Platform"
        const val SEED_ADMIN_LAST_NAME = "Admin"
    }
}
