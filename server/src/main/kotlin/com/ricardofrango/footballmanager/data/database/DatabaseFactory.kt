package com.ricardofrango.footballmanager.data.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun init() {
        try {
            val driverClassName = "org.h2.Driver"
            val jdbcURL = "jdbc:h2:file:./build/db"
            val database = Database.connect(createHikariDataSource(jdbcURL, driverClassName))

            transaction(database) {
                SchemaUtils.create(
                    Users,
                    Clubs,
                    RefreshTokens,
                    ClubMembers,
                    Seasons,
                    AgeGroups,
                    Teams,
                    Players,
                    TeamPlayers,
                    TeamStaff,
                    UserPlayers
                )

                // Data migration: Convert lowercase status/roles to uppercase for Enum compatibility
                // We use double quotes to match Exposed's default quoting and handle H2 keywords like 'role'
                logger.info("Running data migration to uppercase enums...")
                try {
                    exec("""UPDATE "users" SET "status" = UPPER("status")""")
                    exec("""UPDATE "clubs" SET "status" = UPPER("status")""")
                    exec("""UPDATE "club_members" SET "role" = UPPER("role")""")
                    exec("""UPDATE "team_players" SET "status" = UPPER("status")""")
                    exec("""UPDATE "team_staff" SET "role" = UPPER("role")""")
                    exec("""UPDATE "user_players" SET "relationship" = UPPER("relationship")""")
                } catch (e: Exception) {
                    logger.warn("Data migration notice: ${e.message}. This might be normal if the database is already updated.")
                }

                Unit
            }
            logger.info("Database initialized successfully")
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
                maximumPoolSize = 3
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            }
        )

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            suspendTransaction { block() }
        }
}
