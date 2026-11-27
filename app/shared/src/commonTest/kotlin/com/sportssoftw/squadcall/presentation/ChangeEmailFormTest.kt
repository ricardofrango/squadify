package com.sportssoftw.squadcall.presentation

import com.sportssoftw.squadcall.presentation.ui.changeemail.changeEmailFormError
import squadcall.app.shared.generated.resources.Res
import squadcall.app.shared.generated.resources.msg_change_email_same
import squadcall.app.shared.generated.resources.msg_enter_email
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChangeEmailFormTest {
    @Test
    fun aValidNewAddressIsAccepted() {
        assertNull(changeEmailFormError(" new@club.pt ", "old@club.pt"))
        assertNull(changeEmailFormError("new@club.pt", null))
    }

    @Test
    fun anInvalidAddressIsRejected() {
        listOf("", "new", "new@club", "new @club.pt", "@club.pt").forEach {
            assertEquals(Res.string.msg_enter_email, changeEmailFormError(it, "old@club.pt"), it)
        }
    }

    @Test
    fun theCurrentAddressIsRejected() {
        assertEquals(Res.string.msg_change_email_same, changeEmailFormError(" old@club.pt", "old@club.pt"))
    }
}
