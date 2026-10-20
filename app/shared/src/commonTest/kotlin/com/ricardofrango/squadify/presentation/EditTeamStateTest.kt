package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.presentation.ui.editteam.EditTeamUiState
import com.ricardofrango.squadify.presentation.ui.editteam.TeamFormFields
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Edit team: deleting blocks saving, and the dialog names the team as loaded.
class EditTeamStateTest {
    private val fields = TeamFormFields("Sub-15 B", 7)
    private val idle = EditTeamUiState.Idle("Sub-15", "26/27", listOf(7, 9, 11), fields, fields.copy(name = "Sub-15 C"), canDelete = true, matchCount = 4)

    @Test
    fun whileDeletingNothingCanBeSaved() {
        assertTrue(idle.canSave)
        assertTrue(idle.copy(isDeleting = true).busy)
        assertFalse(idle.copy(isDeleting = true).canSave)
    }

    @Test
    fun theDialogUsesTheSavedNameNotWhatIsTyped() {
        assertEquals("Sub-15 B", idle.teamName)
    }
}
