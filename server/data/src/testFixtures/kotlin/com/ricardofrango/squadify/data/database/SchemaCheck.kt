package com.ricardofrango.squadify.data.database

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.DriverManager

/**
 * Each server's schema test (TECHNICAL_DECISIONS §72): runs that server's Flyway migrations on a new,
 * empty database and returns what Exposed would still have to change for [tables] to match. Empty
 * means the migrations create exactly what the shared table definitions describe.
 *
 * The scratch database ([name]) is created on the same Postgres the tests use (the local one, or CI's)
 * and dropped afterwards; it needs a user who may create databases, as `postgres` in both.
 */
object SchemaCheck {
    fun differences(
        host: String, port: Int, user: String, password: String,
        name: String, tables: List<Table>, migrations: String = "classpath:db/migration"
    ): List<String> {
        val server = "jdbc:postgresql://$host:$port"
        fun admin(sql: String) = DriverManager.getConnection("$server/postgres", user, password).use { c ->
            c.createStatement().use { it.execute(sql) }
        }
        admin("DROP DATABASE IF EXISTS $name WITH (FORCE)")
        admin("CREATE DATABASE $name")
        try {
            val url = "$server/$name"
            Flyway.configure().dataSource(url, user, password).locations(migrations).load().migrate()
            val database = Database.connect(url, driver = "org.postgresql.Driver", user = user, password = password)
            return transaction(database) {
                @Suppress("DEPRECATION")
                SchemaUtils.statementsRequiredToActualizeScheme(*tables.toTypedArray(), withLogs = false)
            }
        } finally {
            admin("DROP DATABASE IF EXISTS $name WITH (FORCE)")
        }
    }
}
