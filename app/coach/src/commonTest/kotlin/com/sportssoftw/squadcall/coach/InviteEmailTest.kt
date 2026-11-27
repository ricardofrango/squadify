package com.sportssoftw.squadcall.coach

import com.sportssoftw.squadcall.coach.domain.InviteEmail
import com.sportssoftw.squadcall.coach.presentation.ui.invite.InviteStaffUiState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Emailing an invite code from Invite staff: the address is checked before the server is asked, and Send
// is offered only with one typed and nothing else under way.
class InviteEmailTest {
    @Test
    fun anAddressNeedsANameADomainAndNoSpaces() {
        assertTrue(InviteEmail.isValid(" joao@example.com "))
        assertFalse(InviteEmail.isValid("joao@example"))
        assertFalse(InviteEmail.isValid("joao example@x.com"))
        assertFalse(InviteEmail.isValid("joao.example.com"))
    }

    @Test
    fun sendIsOnlyOfferedWithAnAddressNotWhileSendingOrCreating() {
        assertFalse(InviteStaffUiState.Loaded(emptyList(), email = " ").canSend)
        assertTrue(InviteStaffUiState.Loaded(emptyList(), email = "joao@example.com").canSend)
        assertFalse(InviteStaffUiState.Loaded(emptyList(), email = "joao@example.com", isSending = true).canSend)
        assertFalse(InviteStaffUiState.Loaded(emptyList(), email = "joao@example.com", isCreating = true).canSend)
    }
}
