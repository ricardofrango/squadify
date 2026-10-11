package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.presentation.ui.changeemail.changeEmailFormError
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_change_email_same
import squadify.app.shared.generated.resources.msg_enter_email
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
