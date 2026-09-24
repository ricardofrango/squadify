package com.ricardofrango.squadify.presentation.ui.editclub

import com.ricardofrango.squadify.model.UpdateClubRequest

const val EDIT_CLUB_TAB_DETAILS = 0
const val EDIT_CLUB_TAB_PERMISSIONS = 1

/** Everything `UpdateClubRequest` can carry, held as plain editable fields -- same "blank optional field
 * means null" trick as `ProfileFormFields`, so the request only ever overwrites a field with something
 * the user actually typed. */
data class ClubFormFields(
    val name: String,
    val shortName: String,
    val email: String,
    val phone: String,
    val address: String,
    val stadiumLocation: String
) {
    fun trimmed(): ClubFormFields = ClubFormFields(
        name = name.trim(),
        shortName = shortName.trim(),
        email = email.trim(),
        phone = phone.trim(),
        address = address.trim(),
        stadiumLocation = stadiumLocation.trim()
    )

    fun toRequest(): UpdateClubRequest = trimmed().let {
        UpdateClubRequest(
            name = it.name,
            shortName = it.shortName.takeIf(String::isNotBlank),
            email = it.email.takeIf(String::isNotBlank),
            phone = it.phone.takeIf(String::isNotBlank),
            address = it.address.takeIf(String::isNotBlank),
            stadiumLocation = it.stadiumLocation.takeIf(String::isNotBlank)
        )
    }
}

sealed class EditClubUiState {
    data object Loading : EditClubUiState()
    data class Error(val message: String) : EditClubUiState()

    data class Idle(
        val selectedTab: Int = EDIT_CLUB_TAB_DETAILS,
        val original: ClubFormFields,
        val form: ClubFormFields,
        val isSubmitting: Boolean = false,
        val submitError: String? = null
    ) : EditClubUiState() {
        val isDirty: Boolean get() = form != original
        val isValid: Boolean get() = form.name.isNotBlank()
        val canSave: Boolean get() = isDirty && isValid && !isSubmitting

        // The short name ("CDR") already reads as a logo placeholder as-is; only derive initials from
        // the full name when there's no short name to fall back on.
        val initials: String get() = form.shortName.trim().takeIf { it.isNotBlank() }
            ?: form.name.trim().split(" ")
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .take(2)
                .joinToString("")
    }
}
