package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.presentation.ui.forgotpassword.resetFormError
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_password_min_length
import squadify.app.shared.generated.resources.msg_passwords_do_not_match
import squadify.app.shared.generated.resources.msg_reset_code_format
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResetFormTest {
    @Test
    fun aValidFormHasNoError() {
        assertNull(resetFormError("123456", "new-password", "new-password"))
        assertNull(resetFormError("123 456", "new-password", "new-password"))
    }

    @Test
    fun theCodeMustBeSixDigits() {
        assertEquals(Res.string.msg_reset_code_format, resetFormError("12345", "new-password", "new-password"))
        assertEquals(Res.string.msg_reset_code_format, resetFormError("12345a", "new-password", "new-password"))
        assertEquals(Res.string.msg_reset_code_format, resetFormError("", "new-password", "new-password"))
    }

    @Test
    fun thePasswordNeedsEightCharactersAndMustMatch() {
        assertEquals(Res.string.msg_password_min_length, resetFormError("123456", "short", "short"))
        assertEquals(Res.string.msg_passwords_do_not_match, resetFormError("123456", "new-password", "new-passw0rd"))
    }
}
