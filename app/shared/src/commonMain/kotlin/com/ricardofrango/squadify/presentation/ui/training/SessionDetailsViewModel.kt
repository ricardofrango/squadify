package com.ricardofrango.squadify.presentation.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.ChangeTrainingSessionUseCase
import com.ricardofrango.squadify.domain.usecase.GetTrainingSessionUseCase
import com.ricardofrango.squadify.model.TrainingSessionDetails
import com.ricardofrango.squadify.presentation.errorMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_session_change_failed

data class SessionDetailsUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val details: TrainingSessionDetails? = null,
    val busy: Boolean = false,
    val error: String? = null
)

/** One training session (design 39f): cancel, restore and delete, then reload or leave. */
class SessionDetailsViewModel(
    private val getTrainingSessionUseCase: GetTrainingSessionUseCase,
    private val changeTrainingSessionUseCase: ChangeTrainingSessionUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SessionDetailsUiState())
    val state: StateFlow<SessionDetailsUiState> = _state.asStateFlow()

    // Emitted after a change, so the screens underneath reload; true when the session was deleted.
    private val _changed = MutableSharedFlow<Boolean>()
    val changed: SharedFlow<Boolean> = _changed.asSharedFlow()

    private var sessionId: String? = null

    fun load(sessionId: String, force: Boolean = false) {
        if (!force && this.sessionId == sessionId && _state.value.details != null) return
        this.sessionId = sessionId
        viewModelScope.launch {
            if (_state.value.details == null) _state.update { it.copy(loading = true, loadFailed = false) }
            try {
                val details = getTrainingSessionUseCase(sessionId)
                _state.update { it.copy(loading = false, loadFailed = false, details = details) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadFailed = it.details == null) }
            }
        }
    }

    fun cancel() = change(deleted = false) { changeTrainingSessionUseCase.cancel(it) }
    fun restore() = change(deleted = false) { changeTrainingSessionUseCase.restore(it) }
    fun delete() = change(deleted = true) { changeTrainingSessionUseCase.delete(it) }

    private fun change(deleted: Boolean, action: suspend (String) -> Unit) {
        val id = sessionId ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                action(id)
                _changed.emit(deleted)
                _state.update { it.copy(busy = false) }
                if (!deleted) load(id, force = true)
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_session_change_failed)
                _state.update { it.copy(busy = false, error = message) }
            }
        }
    }
}
