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
                    ClubMembers,
                    Seasons,
                    AgeGroups,
                    Teams,
                    Players,
                    TeamPlayers,
                    TeamStaff,
                    UserPlayers
                )
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
