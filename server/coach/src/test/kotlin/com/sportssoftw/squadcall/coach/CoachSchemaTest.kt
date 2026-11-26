package com.sportssoftw.squadcall.coach

import com.sportssoftw.squadcall.coach.data.coachOnlyTables
import com.sportssoftw.squadcall.coach.config.CoachConfig
import com.sportssoftw.squadcall.data.database.SchemaCheck
import com.sportssoftw.squadcall.data.database.sharedTables
import org.junit.Test
import kotlin.test.assertEquals

// The coach migrations create exactly the shared tables, as Tables.kt describes them (TECHNICAL_DECISIONS
// §72). A change to a shared table with a clubs migration but none here fails this.
class CoachSchemaTest {
    @Test
    fun theMigrationsMatchTheSharedAndCoachTables() {
        val differences = SchemaCheck.differences(
            CoachConfig.databaseHost, CoachConfig.databasePort, CoachConfig.databaseUser, CoachConfig.databasePassword,
            name = "squadcall_schema_check_coach",
            tables = sharedTables + coachOnlyTables
        )
        assertEquals(emptyList(), differences)
    }
}
