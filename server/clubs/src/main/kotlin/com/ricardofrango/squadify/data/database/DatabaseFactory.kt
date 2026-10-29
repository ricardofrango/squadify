package com.ricardofrango.squadify.data.database

import com.ricardofrango.squadify.config.AppEnvironment
import com.ricardofrango.squadify.config.ServerConfig
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

object DatabaseFactory : TransactionManager {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Connects (migrating first) and returns the connection pool, for the caller to close when the
     * server stops. Each module closes its own: a reload in development starts the new one before
     * stopping the old one.
     */
    fun init(): HikariDataSource {
        try {
            val driverClassName = "org.postgresql.Driver"
            val jdbcURL = "jdbc:postgresql://${ServerConfig.databaseHost}:${ServerConfig.databasePort}/${ServerConfig.databaseName}"
            val dataSource = createHikariDataSource(jdbcURL, driverClassName)

            // Schema changes now go through versioned files in `src/main/resources/db/migration`
            // (V1__baseline.sql onward), applied here on every boot -- replaces the previous
            // `SchemaUtils.createMissingTablesAndColumns` approach (see TECHNICAL_DECISIONS.md).
            // `baselineOnMigrate` + `baselineVersion("1")` mark any database that already has this
            // schema (dev/staging/production, all migrated the old way before this change) as already
            // being at V1 *without* re-running V1's SQL (which would fail -- the tables already exist);
            // only a genuinely empty database (a fresh local Postgres, a fresh CI run) actually executes
            // V1 end-to-end. Every migration after V1 always actually runs, everywhere.
            Flyway.configure()
                .dataSource(dataSource)
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load()
                .migrate()

            val database = Database.connect(dataSource)

            transaction(database) {
                // One-off, idempotent backfill for the old single `birth_year` column -> the new
                // `start_year`/`end_year` range (see AgeGroup in core Models.kt), predating Flyway --
                // kept running unconditionally rather than folded into the migration chain, since it's
                // already a no-op once `birth_year` is gone (the `start_year = 0` guard means it never
                // touches rows that already have a real range set) and this project can't verify every
                // real environment has already been through it.
                exec(
                    """
                    DO $$
                    BEGIN
                        IF EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_name = 'age_groups' AND column_name = 'birth_year'
                        ) THEN
                            UPDATE age_groups SET start_year = birth_year, end_year = birth_year
                            WHERE start_year = 0 AND end_year = 0;
                            ALTER TABLE age_groups DROP COLUMN birth_year CASCADE;
                        END IF;
                    END $$;
                    """.trimIndent()
                )
            }
            logger.info("Database '${ServerConfig.databaseName}' initialized successfully")
            return dataSource
        } catch (e: Exception) {
            logger.error("Failed to initialize database", e)
            throw e
        }
    }

    private fun createHikariDataSource(url: String, driver: String) =
        HikariDataSource(
            HikariConfig().apply {
                driverClassName = driver
                jdbcUrl = url
                username = ServerConfig.databaseUser
                password = ServerConfig.databasePassword
                maximumPoolSize = if (ServerConfig.environment == AppEnvironment.PRODUCTION) 10 else 3
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            }
        )

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            suspendTransaction { block() }
        }

    override suspend fun <T> runInTransaction(block: suspend () -> T): T = dbQuery(block)
}
