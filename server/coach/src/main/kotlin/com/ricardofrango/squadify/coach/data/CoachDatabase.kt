package com.ricardofrango.squadify.coach.data

import com.ricardofrango.squadify.coach.config.CoachConfig
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.DriverManager

/**
 * The coach database: connects, runs the coach migrations (`db/migration` in this module) and runs the
 * shared use cases' transactions. Locally the database is created when it doesn't exist yet, so the
 * coach server runs against docker-compose's Postgres (and CI's) with nothing to set up.
 */
object CoachDatabase : TransactionManager {
    fun init(): HikariDataSource {
        val server = "jdbc:postgresql://${CoachConfig.databaseHost}:${CoachConfig.databasePort}"
        if (!CoachConfig.deployed) createIfMissing(server)

        val dataSource = HikariDataSource(
            HikariConfig().apply {
                driverClassName = "org.postgresql.Driver"
                jdbcUrl = "$server/${CoachConfig.databaseName}"
                username = CoachConfig.databaseUser
                password = CoachConfig.databasePassword
                maximumPoolSize = if (CoachConfig.deployed) 10 else 3
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            }
        )
        Flyway.configure().dataSource(dataSource).load().migrate()
        Database.connect(dataSource)
        return dataSource
    }

    private fun createIfMissing(server: String) {
        DriverManager.getConnection("$server/postgres", CoachConfig.databaseUser, CoachConfig.databasePassword).use { c ->
            val exists = c.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?").use { st ->
                st.setString(1, CoachConfig.databaseName)
                st.executeQuery().use { it.next() }
            }
            if (!exists) c.createStatement().use { it.execute("CREATE DATABASE \"${CoachConfig.databaseName}\"") }
        }
    }

    override suspend fun <T> runInTransaction(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction { block() } }
}
