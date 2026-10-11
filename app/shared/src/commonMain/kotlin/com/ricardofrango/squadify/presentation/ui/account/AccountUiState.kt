package com.ricardofrango.squadify.presentation.ui.account

import com.ricardofrango.squadify.model.UpdateProfileRequest
import com.ricardofrango.squadify.model.UserStatus
import kotlinx.datetime.LocalDate

/** Everything `UpdateProfileRequest` can carry, held as plain editable fields (phone as a raw string,
 * same trick `PlayerFormFields` uses, so an absent phone has an obvious "empty" representation). */
data class ProfileFormFields(
    val firstName: String,
    val lastName: String,
    val phone: String
) {
    fun toRequest(): UpdateProfileRequest = UpdateProfileRequest(
        firstName = firstName.trim(),
        lastName = lastName.trim(),
        phone = phone.trim().takeIf { it.isNotBlank() }
    )
}

data class PasswordFormFields(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val currentVisible: Boolean = false,
    val newVisible: Boolean = false,
    val confirmVisible: Boolean = false
) {
    val newPasswordTooShort: Boolean get() = newPassword.isNotEmpty() && newPassword.length < 8
    val confirmMismatch: Boolean get() = confirmPassword.isNotEmpty() && confirmPassword != newPassword
    val isValid: Boolean get() =
        currentPassword.isNotBlank() && newPassword.length >= 8 && newPassword == confirmPassword
}

sealed class AccountUiState {
    data object Loading : AccountUiState()
    data class Error(val message: String) : AccountUiState()

    /**
     * [original] is what the profile looked like when the screen loaded -- diffed against [form] to
     * decide whether the top-bar Save is enabled at all, same "disabled until dirty and valid" pattern
     * as EditPlayerScreen. Profile save and password change are two independent actions (their own
     * submitting/error state each) so one submitting doesn't block or get clobbered by the other.
     */
    data class Idle(
        val email: String,
        val memberSince: LocalDate,
        val status: UserStatus,
        val original: ProfileFormFields,
        val form: ProfileFormFields,
        val isSubmittingProfile: Boolean = false,
        val profileSubmitError: String? = null,
        val passwordForm: PasswordFormFields = PasswordFormFields(),
        val isSubmittingPassword: Boolean = false,
        val passwordSubmitError: String? = null,
        val passwordChanged: Boolean = false,
        // Just back from Change email: shows "Email changed" under the email.
        val emailChanged: Boolean = false,
        // Third wrong current password on Change password: signed out; the screen shows SignedOutAlert.
        val signedOut: Boolean = false
    ) : AccountUiState() {
        val isDirty: Boolean get() = form != original
        val isValid: Boolean get() = form.firstName.isNotBlank() && form.lastName.isNotBlank()
        val canSaveProfile: Boolean get() = isDirty && isValid && !isSubmittingProfile
        val canChangePassword: Boolean get() = passwordForm.isValid && !isSubmittingPassword

        val initials: String get() = listOf(form.firstName, form.lastName)
            .mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }
            .joinToString("")
    }
}

sealed class AccountNavigationEvent {
    data object NavigateToLogin : AccountNavigationEvent()
}
