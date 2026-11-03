package com.ricardofrango.squadify.coach.presentation.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.coach.domain.GetSpacesUseCase
import com.ricardofrango.squadify.domain.usecase.DeleteClubLogoUseCase
import com.ricardofrango.squadify.domain.usecase.UploadClubLogoUseCase
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.SpacePlan
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the logo didn't change, shown under it. */
enum class LogoProblem { TOO_BIG, UPLOAD_FAILED, REMOVE_FAILED }

sealed class MySpaceUiState {
    data object Loading : MySpaceUiState()
    data object Failed : MySpaceUiState()

    /**
     * The owner's space, with its [plan] and [ageGroupsThisSeason] for the Plan card; [isUpdatingLogo]
     * while a logo is being saved or removed.
     */
    data class Loaded(
        val space: Club,
        val plan: SpacePlan,
        val ageGroupsThisSeason: Int = 0,
        val isUpdatingLogo: Boolean = false,
        val logoProblem: LogoProblem? = null
    ) : MySpaceUiState()
}

/**
 * The owner's own space (the ☰ menu's My space): its plan, and its logo, saved straight away as in the clubs app's
 * Club settings. A space is a club on the coach server, so the logo is the club's (`/clubs/{id}/logo`).
 */
class MySpaceViewModel(
    private val getSpacesUseCase: GetSpacesUseCase,
    private val uploadClubLogoUseCase: UploadClubLogoUseCase,
    private val deleteClubLogoUseCase: DeleteClubLogoUseCase
) : ViewModel() {
    private val _state = MutableStateFlow<MySpaceUiState>(MySpaceUiState.Loading)
    val state: StateFlow<MySpaceUiState> = _state.asStateFlow()

    /** Each time the logo changes, so Home reloads its rows. */
    private val _logoChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val logoChanged: SharedFlow<Unit> = _logoChanged.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = MySpaceUiState.Loading
            _state.value = try {
                getSpacesUseCase().firstOrNull { it.isOwner }?.let { MySpaceUiState.Loaded(it.space, it.plan, it.ageGroupsThisSeason) } ?: MySpaceUiState.Failed
            } catch (e: Exception) {
                MySpaceUiState.Failed
            }
        }
    }

    /** Uploads [bytes] as the logo; the server checks and resizes it. Too big for the server, it's refused here. */
    fun onLogoPicked(bytes: ByteArray) {
        if (bytes.size > MAX_LOGO_BYTES) {
            updateLoaded { it.copy(logoProblem = LogoProblem.TOO_BIG) }
            return
        }
        changeLogo(LogoProblem.UPLOAD_FAILED) { uploadClubLogoUseCase(it, bytes) }
    }

    fun onRemoveLogo() = changeLogo(LogoProblem.REMOVE_FAILED) { deleteClubLogoUseCase(it); null }

    private fun changeLogo(failure: LogoProblem, action: suspend (spaceId: String) -> String?) {
        val loaded = _state.value as? MySpaceUiState.Loaded ?: return
        if (loaded.isUpdatingLogo) return
        viewModelScope.launch {
            updateLoaded { it.copy(isUpdatingLogo = true, logoProblem = null) }
            try {
                val url = action(loaded.space.id)
                _logoChanged.tryEmit(Unit)
                updateLoaded { it.copy(space = it.space.copy(logoUrl = url), isUpdatingLogo = false) }
            } catch (e: Exception) {
                updateLoaded { it.copy(isUpdatingLogo = false, logoProblem = failure) }
            }
        }
    }

    private fun updateLoaded(block: (MySpaceUiState.Loaded) -> MySpaceUiState.Loaded) =
        _state.update { (it as? MySpaceUiState.Loaded)?.let(block) ?: it }
}

// The server's limit (LogoImage.MAX_UPLOAD_BYTES): checked here too, so a big image isn't sent for nothing.
internal const val MAX_LOGO_BYTES = 5 * 1024 * 1024
