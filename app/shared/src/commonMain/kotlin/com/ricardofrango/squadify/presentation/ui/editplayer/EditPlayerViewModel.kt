package com.ricardofrango.squadify.presentation.ui.editplayer

import com.ricardofrango.squadify.domain.config.AppFeatures

import com.ricardofrango.squadify.domain.usecase.DeletePlayerPhotoUseCase
import com.ricardofrango.squadify.domain.usecase.UploadPlayerPhotoUseCase
import com.ricardofrango.squadify.presentation.PhotoChanger
import com.ricardofrango.squadify.presentation.ui.components.PhotoState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetPlayerDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.UpdatePlayerUseCase
import com.ricardofrango.squadify.presentation.errorMessage
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_edit_player_failed
import squadify.app.shared.generated.resources.msg_failed_to_load_player
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class EditPlayerViewModel(
    private val getPlayerDetailsUseCase: GetPlayerDetailsUseCase,
    private val updatePlayerUseCase: UpdatePlayerUseCase,
    private val uploadPlayerPhotoUseCase: UploadPlayerPhotoUseCase,
    private val deletePlayerPhotoUseCase: DeletePlayerPhotoUseCase,
    features: AppFeatures
) : ViewModel() {

    // Without the server's image storage (the coach app, for now), no photo on the form.
    val showsPhoto: Boolean = features.photos

    private val _state = MutableStateFlow<EditPlayerUiState>(EditPlayerUiState.Loading)
    val state: StateFlow<EditPlayerUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<EditPlayerNavigationEvent>()
    val navigationEvent: SharedFlow<EditPlayerNavigationEvent> = _navigationEvent.asSharedFlow()

    fun load(playerId: String) {
        viewModelScope.launch {
            _state.update { EditPlayerUiState.Loading }
            try {
                val profile = getPlayerDetailsUseCase(playerId)
                val fields = PlayerFormFields(
                    firstName = profile.player.firstName,
                    lastName = profile.player.lastName,
                    birthDateIso = profile.player.birthDate.toString(),
                    shirtNumber = profile.player.shirtNumber,
                    position = profile.player.position,
                    heightCm = profile.player.heightCm,
                    weightKg = profile.player.weightKg,
                    emergencyContact = profile.player.emergencyContact
                )
                _state.update {
                    EditPlayerUiState.Idle(
                        ageGroupId = profile.ageGroupId,
                        ageGroupLabel = profile.ageGroupLabel,
                        seasonLabel = profile.seasonLabel,
                        original = fields,
                        form = fields,
                        photo = PhotoState(url = profile.player.photoUrl)
                    )
                }
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_failed_to_load_player)
                _state.update { EditPlayerUiState.Error(message) }
            }
        }
    }

    private val photoChanger = PhotoChanger(
        scope = viewModelScope,
        read = { (_state.value as? EditPlayerUiState.Idle)?.photo },
        write = { change -> _state.update { (it as? EditPlayerUiState.Idle)?.let { idle -> idle.copy(photo = change(idle.photo)) } ?: it } }
    )

    /** Saves the photo straight away; [onChanged] tells the screens below (Player details) to reload. */
    fun onPhotoPicked(playerId: String, bytes: ByteArray, onChanged: () -> Unit) =
        photoChanger.upload(bytes, { uploadPlayerPhotoUseCase(playerId, it) }, onChanged)

    fun onRemovePhoto(playerId: String, onChanged: () -> Unit) =
        photoChanger.remove({ deletePlayerPhotoUseCase(playerId) }, onChanged)

    fun updateForm(transform: (PlayerFormFields) -> PlayerFormFields) {
        _state.update { current ->
            if (current is EditPlayerUiState.Idle) {
                current.copy(form = transform(current.form), submitError = null)
            } else {
                current
            }
        }
    }

    fun onSaveClicked(playerId: String) {
        val current = _state.value as? EditPlayerUiState.Idle ?: return
        if (!current.canSave) return

        viewModelScope.launch {
            _state.update { (it as EditPlayerUiState.Idle).copy(isSubmitting = true, submitError = null) }
            try {
                updatePlayerUseCase(playerId, current.form.toRequest().copy(ageGroupId = current.ageGroupId))
                _navigationEvent.emit(EditPlayerNavigationEvent.NavigateBackWithPlayerUpdated)
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_edit_player_failed)
                _state.update {
                    (it as EditPlayerUiState.Idle).copy(isSubmitting = false, submitError = message)
                }
            }
        }
    }
}
