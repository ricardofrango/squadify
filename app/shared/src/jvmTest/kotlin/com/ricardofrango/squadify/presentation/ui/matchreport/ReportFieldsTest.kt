package com.ricardofrango.squadify.presentation.ui.matchreport

import com.ricardofrango.squadify.model.MatchReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ReportFieldsTest {

    private fun report(tactic: String?) = MatchReport("m", tactic = tactic, updatedAt = Instant.fromEpochMilliseconds(0), updatedByUserId = "u")

    @Test
    fun theStartingFormationIsTheTacticUntilTheReportIsSaved() {
        assertEquals("2-3-1", ReportFields.from(null, "2-3-1").tactic)
        assertEquals("", ReportFields.from(null, null).tactic)
        // Once saved, the report's own tactic stands -- even one the coach cleared.
        assertEquals("3-2-1 high press", ReportFields.from(report("3-2-1 high press"), "2-3-1").tactic)
        assertEquals("", ReportFields.from(report(null), "2-3-1").tactic)
    }
}
