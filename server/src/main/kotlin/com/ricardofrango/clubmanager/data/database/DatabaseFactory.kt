package com.ricardofrango.clubmanager.data.database

import com.ricardofrango.clubmanager.domain.service.TransactionManager
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
            val driverClassName = "org.h2.Driver"
            // Path relative to the working dir (the project root when run via Gradle); AUTO_SERVER=TRUE
            // allows a second connection (e.g. the H2 web console) alongside the running server.
            val jdbcURL = "jdbc:h2:file:./server/build/db;AUTO_SERVER=TRUE"
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

                // Data migration: Convert lowercase status/roles to uppercase for Enum compatibility
                logger.info("Normalizing data for enums...")
                try {
                    // Using uppercase names as suggested by H2 and catching errors if tables are empty/migrated
                    exec("""UPDATE USERS SET status = 'ACTIVE' WHERE status = 'active'""")
                    exec("""UPDATE USERS SET status = 'INACTIVE' WHERE status = 'inactive'""")
                    exec("""UPDATE CLUBS SET status = 'ACTIVE' WHERE status = 'active'""")
                    exec("""UPDATE CLUBS SET status = 'INACTIVE' WHERE status = 'inactive'""")
                    exec("""UPDATE CLUB_MEMBERS SET role = UPPER(role)""")
                    exec("""UPDATE AGE_GROUP_STAFF SET role = UPPER(role)""")
                    exec("""UPDATE USER_PLAYERS SET relationship = UPPER(relationship)""")
                    exec("""UPDATE SEASONS SET type = UPPER(type)""")
                    exec("""UPDATE MATCH_EVENTS SET type = UPPER(type)""")
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
                // Intentionally blank: this is a local, file-based H2 database reachable only from
                // this machine (the web console no longer accepts remote connections either -- see
                // Application.kt). Not changed to avoid invalidating the existing local DB file's
                // stored credentials (H2 persists them at creation time; changing the connecting
                // password here would lock out the current dev database until it's recreated).
                // Superseded once production moves to PostgreSQL with real credential handling.
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

    override suspend fun <T> runInTransaction(block: suspend () -> T): T = dbQuery(block)
}
