package com.sportssoftw.squadcall.data.database

import com.sportssoftw.squadcall.config.ServerConfig
import org.junit.Test
import kotlin.test.assertEquals

// The clubs migrations create exactly the tables the shared definitions describe, every one of them
// (TECHNICAL_DECISIONS §72). A change to a shared table that misses its migration here fails this.
class ClubsSchemaTest {
    @Test
    fun theMigrationsMatchTheTables() {
        val differences = SchemaCheck.differences(
            ServerConfig.databaseHost, ServerConfig.databasePort, ServerConfig.databaseUser, ServerConfig.databasePassword,
            name = "squadcall_schema_check_clubs",
            tables = sharedTables + clubsOnlyTables
        )
        assertEquals(emptyList(), differences)
    }
}
