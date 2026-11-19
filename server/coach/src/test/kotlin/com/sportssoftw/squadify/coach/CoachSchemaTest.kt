package com.sportssoftw.squadify.coach

import com.sportssoftw.squadify.coach.data.coachOnlyTables
import com.sportssoftw.squadify.coach.config.CoachConfig
import com.sportssoftw.squadify.data.database.SchemaCheck
import com.sportssoftw.squadify.data.database.sharedTables
import org.junit.Test
import kotlin.test.assertEquals

// The coach migrations create exactly the shared tables, as Tables.kt describes them (TECHNICAL_DECISIONS
// §72). A change to a shared table with a clubs migration but none here fails this.
class CoachSchemaTest {
    @Test
    fun theMigrationsMatchTheSharedAndCoachTables() {
        val differences = SchemaCheck.differences(
            CoachConfig.databaseHost, CoachConfig.databasePort, CoachConfig.databaseUser, CoachConfig.databasePassword,
            name = "squadify_schema_check_coach",
            tables = sharedTables + coachOnlyTables
        )
        assertEquals(emptyList(), differences)
    }
}
