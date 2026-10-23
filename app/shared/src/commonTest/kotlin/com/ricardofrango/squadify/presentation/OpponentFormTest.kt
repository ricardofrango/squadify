package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.presentation.ui.opponents.OpponentForm
import com.ricardofrango.squadify.presentation.ui.opponents.OpponentFormUiState
import com.ricardofrango.squadify.presentation.ui.opponents.OpponentLogoDraft
import com.ricardofrango.squadify.presentation.ui.opponents.isDuplicateOpponentName
import com.ricardofrango.squadify.presentation.ui.opponents.toCreateRequest
import com.ricardofrango.squadify.presentation.ui.opponents.toForm
import com.ricardofrango.squadify.presentation.ui.opponents.toUpdateRequest
import com.ricardofrango.squadify.presentation.ui.opponents.typedShortName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Create / Edit opponent (design 38e): duplicate names, the short name as typed, and what's sent.
class OpponentFormTest {
    private val clubOpponents = listOf(Opponent("o1", "c", "CD Mafra"), Opponent("o2", "c", "AD Fontelas"))

    @Test
    fun aNameTheClubHasIsADuplicateIgnoringCaseAndSpaces() {
        assertTrue(isDuplicateOpponentName("  cd mafra ", clubOpponents))
        assertFalse(isDuplicateOpponentName("CD Mafra B", clubOpponents))
        assertFalse(isDuplicateOpponentName("   ", clubOpponents))
    }

    @Test
    fun theOpponentBeingEditedIsNotItsOwnDuplicate() {
        assertFalse(isDuplicateOpponentName("CD Mafra", clubOpponents, exceptId = "o1"))
    }

    @Test
    fun theShortNameIsUppercasedAndCappedAtFive() {
        assertEquals("SPORT", typedShortName("sporting"))
    }

    @Test
    fun emptyOptionalFieldsAreLeftOutOfTheRequest() {
        val request = OpponentForm(name = " Sporting Lourel ", stadiumName = "  ", notes = "Parking behind").toCreateRequest(ageGroupId = "ag")
        assertEquals("Sporting Lourel", request.name)
        assertNull(request.stadiumName)
        assertNull(request.shortName)
        assertEquals("Parking behind", request.notes)
        assertEquals("ag", request.ageGroupId)
    }

    @Test
    fun editSavesOnlyOnceSomethingChanged() {
        val original = Opponent("o1", "c", "AD Fontelas", "ADF", stadiumName = "Campo das Fontelas").toForm()
        val loaded = OpponentFormUiState(form = original, original = original)
        assertFalse(loaded.canSubmit)
        assertTrue(loaded.copy(form = original.copy(notes = "Parking behind")).canSubmit)
    }

    @Test
    fun anEditSendsEveryFieldSoAnEmptiedOneIsCleared() {
        val request = OpponentForm(name = "AD Fontelas", shortName = "ADF", stadiumAddress = "").toUpdateRequest()
        assertEquals("", request.stadiumAddress)
        assertEquals("ADF", request.shortName)
    }

    // The logo is saved with the form: picking or removing it is a change on its own.
    @Test
    fun pickingOrRemovingTheLogoIsEnoughToSave() {
        val form = OpponentForm(name = "CD Mafra")
        val editing = OpponentFormUiState(form = form, original = form, logo = OpponentLogoDraft("/opponents/o1/logo/a.png"))
        assertFalse(editing.canSubmit)
        assertTrue(editing.copy(logo = editing.logo.pick(byteArrayOf(1))).canSubmit)
        assertTrue(editing.copy(logo = editing.logo.remove()).canSubmit)
    }

    @Test
    fun aPickedLogoReplacesTheShownOneAndRemovingItGoesBackToNone() {
        val current = OpponentLogoDraft("/opponents/o1/logo/a.png")
        val picked = current.pick(byteArrayOf(1))
        assertNull(picked.shownUrl)
        assertTrue(picked.hasLogo)

        val removed = picked.remove()
        assertNull(removed.picked)
        assertTrue(removed.removed)
        assertFalse(removed.hasLogo)
    }

    @Test
    fun removingAPickedLogoWhenThereWasNoneLeavesNothingToDo() {
        val draft = OpponentLogoDraft().pick(byteArrayOf(1)).remove()
        assertFalse(draft.changed)
        assertFalse(draft.hasLogo)
    }
}
