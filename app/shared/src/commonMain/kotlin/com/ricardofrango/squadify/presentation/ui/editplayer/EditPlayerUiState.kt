package com.ricardofrango.squadify.presentation.ui.editplayer

import com.ricardofrango.squadify.model.PlayerPosition
import com.ricardofrango.squadify.model.UpdatePlayerRequest
import kotlinx.datetime.LocalDate

/**
 * Everything `UpdatePlayerRequest` can carry, held as plain editable fields (dates as ISO strings, same
 * trick `CreatePlayersScreen` uses, so a not-yet-picked date has an obvious "empty" representation).
 */
data class PlayerFormFields(
    val firstName: String,
    val lastName: String,
    val birthDateIso: String?,
    val shirtNumber: Int? = null,
    val position: PlayerPosition? = null,
    val heightCm: Int? = null,
    val weightKg: Int? = null,
    val emergencyContact: String? = null
) {
    fun toRequest(): UpdatePlayerRequest = UpdatePlayerRequest(
        firstName = firstName.trim(),
        lastName = lastName.trim(),
        birthDate = birthDateIso?.let(LocalDate::parse),
        shirtNumber = shirtNumber,
        position = position,
        heightCm = heightCm,
        weightKg = weightKg,
        emergencyContact = emergencyContact?.trim()?.takeIf { it.isNotBlank() }
    )
}

sealed class EditPlayerUiState {
    data object Loading : EditPlayerUiState()
    data class Error(val message: String) : EditPlayerUiState()

    /**
     * [original] is what the player looked like when the screen opened -- diffed against [form] to
     * decide whether Save is enabled at all (design 17B: an untouched form's Save stays disabled with
     * "Nothing changed yet."). The optional-fields banner (17B) is keyed off [original] rather than the
     * live [form], since it's describing the player's on-file state, not this edit session.
     */
    data class Idle(
        // Carried through (not user-editable) so a save can be authorized via
        // ConfigurablePermission.EDIT_PLAYER as this player's own age-group coach, not just a
        // coordinator+ -- see CreatePlayersScreen's identical ageGroupId threading.
        val ageGroupId: String?,
        val ageGroupLabel: String?,
        val seasonLabel: String?,
        val original: PlayerFormFields,
        val form: PlayerFormFields,
        val isSubmitting: Boolean = false,
        val submitError: String? = null
    ) : EditPlayerUiState() {
        val isDirty: Boolean get() = form != original
        val isValid: Boolean get() = form.firstName.isNotBlank() && form.lastName.isNotBlank() && form.birthDateIso != null
        val canSave: Boolean get() = isDirty && isValid && !isSubmitting
        val optionalFieldsNeverSet: Boolean get() = original.shirtNumber == null &&
            original.position == null &&
            original.heightCm == null &&
            original.weightKg == null &&
            original.emergencyContact.isNullOrBlank()
    }
}

sealed class EditPlayerNavigationEvent {
    data object NavigateBackWithPlayerUpdated : EditPlayerNavigationEvent()
}
