package com.ricardofrango.squadify.data.database

import com.ricardofrango.squadify.config.AppEnvironment
import com.ricardofrango.squadify.config.ServerConfig
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

object DatabaseFactory : TransactionManager {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun init() {
        try {
            val driverClassName = "org.postgresql.Driver"
            val jdbcURL = "jdbc:postgresql://${ServerConfig.databaseHost}:${ServerConfig.databasePort}/${ServerConfig.databaseName}"
            val database = Database.connect(createHikariDataSource(jdbcURL, driverClassName))

            transaction(database) {
                SchemaUtils.createMissingTablesAndColumns(
                    Users,
                    Clubs,
                    RefreshTokens,
                    ClubMembers,
                    Seasons,
                    AgeGroups,
                    Teams,
                    Players,
                    AgeGroupPlayers,
                    AgeGroupStaff,
                    UserPlayers,
                    Invites,
                    Opponents,
                    Matches,
                    MatchCallUps,
                    MatchEvents
                )
                // One-off, idempotent backfill for the old single `birth_year` column -> the new
                // `start_year`/`end_year` range (see AgeGroup in core Models.kt). Safe to run on every
                // boot: a no-op once `birth_year` is gone, and the `start_year = 0` guard means it never
                // touches rows that already have a real range set.
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
