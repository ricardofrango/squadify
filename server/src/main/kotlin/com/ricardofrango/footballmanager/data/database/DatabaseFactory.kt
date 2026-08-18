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
            // Using absolute path and AUTO_SERVER=TRUE to allow multiple connections (Server + Web Console)
            val jdbcURL = "jdbc:h2:file:/Users/ricardofrango/Dev/KMM/FootballManager/server/build/db;AUTO_SERVER=TRUE"
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
                logger.info("Normalizing data for enums...")
                try {
                    // Using uppercase names as suggested by H2 and catching errors if tables are empty/migrated
                    exec("""UPDATE USERS SET status = 'ACTIVE' WHERE status = 'active'""")
                    exec("""UPDATE USERS SET status = 'INACTIVE' WHERE status = 'inactive'""")
                    exec("""UPDATE CLUBS SET status = 'ACTIVE' WHERE status = 'active'""")
                    exec("""UPDATE CLUBS SET status = 'INACTIVE' WHERE status = 'inactive'""")
                    exec("""UPDATE CLUB_MEMBERS SET role = UPPER(role)""")
                    exec("""UPDATE TEAM_PLAYERS SET status = UPPER(status)""")
                    exec("""UPDATE TEAM_STAFF SET role = UPPER(role)""")
                    exec("""UPDATE USER_PLAYERS SET relationship = UPPER(relationship)""")
                } catch (e: Exception) {
                    logger.warn("Data migration notice: ${e.message}")
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
                username = "sa"
                password = ""
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
