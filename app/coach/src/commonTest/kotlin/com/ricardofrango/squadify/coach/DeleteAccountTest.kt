package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.presentation.ui.deleteaccount.DeleteAccountUiState
import com.ricardofrango.squadify.model.AccountDeletionSummary
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Deleting the account (REQUIREMENTS §5c): only with a password typed, and not twice at once.
class DeleteAccountTest {
    @Test
    fun deletingNeedsThePasswordAndOneAtATime() {
        val idle = DeleteAccountUiState.Idle(AccountDeletionSummary())
        assertFalse(idle.canDelete)
        assertTrue(idle.copy(password = "secret").canDelete)
        assertFalse(idle.copy(password = "secret", isDeleting = true).canDelete)
    }
}
