package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.presentation.ui.editagegroup.AgeGroupFormFields
import com.ricardofrango.squadify.presentation.ui.editagegroup.EditAgeGroupUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Edit age group's save rules, mirroring UpdateAgeGroupUseCase on the server.
class EditAgeGroupFormTest {
    private val original = AgeGroupFormFields("Sub-15", twoYears = true, startYear = 2011, endYear = 2010, teamSize = 11)

    private fun state(form: AgeGroupFormFields, pool: List<Int> = listOf(2010, 2011)) =
        EditAgeGroupUiState.Idle("2025/26", listOf(7, 9, 11), emptySet(), pool, original, form)

    @Test
    fun anUntouchedFormCannotBeSaved() {
        assertFalse(state(original).canSave)
    }

    @Test
    fun narrowingTheSpanPastAPoolPlayerBlocksSaving() {
        val s = state(original.copy(twoYears = false, startYear = 2011, endYear = 2011))
        assertEquals(1, s.shutOut)
        assertFalse(s.canSave)
    }

    @Test
    fun anInvertedTwoYearSpanBlocksSaving() {
        val s = state(original.copy(startYear = 2009, endYear = 2010), pool = emptyList())
        assertTrue(s.inverted)
        assertFalse(s.canSave)
    }

    @Test
    fun onlyChangedFieldsAreSent() {
        val request = state(original.copy(name = " Sub-15 Futsal ", teamSize = 9)).toRequest()
        assertEquals("Sub-15 Futsal", request.name)
        assertEquals(9, request.teamSize)
        assertNull(request.startYear)
        assertNull(request.endYear)
    }

    @Test
    fun aSpanSharingAYearWithAnotherAgeGroupBlocksSaving() {
        // Another age group covers 2012: switching to 2012-2011 overlaps it, even though not identical.
        val s = EditAgeGroupUiState.Idle("2025/26", listOf(11), setOf(2012), emptyList(), original, original.copy(startYear = 2012, endYear = 2011))
        assertTrue(s.collides)
        assertFalse(s.canSave)
    }
}
