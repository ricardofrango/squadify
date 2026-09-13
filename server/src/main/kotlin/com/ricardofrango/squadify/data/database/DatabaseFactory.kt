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
