package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.config.CoachConfig
import java.sql.DriverManager

/**
 * Drops the coach tests' own database once they've run (Gradle's `dropTestDatabase`, which `test` is
 * finalized by, so it runs when tests fail too): the tests sign up, set up spaces and play games, and
 * none of that should stay behind. Only ever a `_test` database on a local Postgres, never one deployed.
 */
fun main() {
    val name = CoachConfig.databaseName
    check(name.endsWith("_test") && !CoachConfig.deployed) { "Refusing to drop $name: not a local test database." }
    val url = "jdbc:postgresql://${CoachConfig.databaseHost}:${CoachConfig.databasePort}/postgres"
    DriverManager.getConnection(url, CoachConfig.databaseUser, CoachConfig.databasePassword).use { connection ->
        connection.createStatement().use { it.execute("DROP DATABASE IF EXISTS $name WITH (FORCE)") }
    }
    println("Dropped the coach tests' database, $name.")
}
