package com.ricardofrango.squadify.data.database

import com.ricardofrango.squadify.config.ServerConfig
import org.junit.Test
import kotlin.test.assertEquals

// The clubs migrations create exactly the tables the shared definitions describe, every one of them
// (TECHNICAL_DECISIONS §72). A change to a shared table that misses its migration here fails this.
class ClubsSchemaTest {
    @Test
    fun theMigrationsMatchTheTables() {
        val differences = SchemaCheck.differences(
            ServerConfig.databaseHost, ServerConfig.databasePort, ServerConfig.databaseUser, ServerConfig.databasePassword,
            name = "squadify_schema_check_clubs",
            tables = sharedTables + clubsOnlyTables
        )
        assertEquals(emptyList(), differences)
    }
}
