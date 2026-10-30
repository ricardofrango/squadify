package com.ricardofrango.squadify.coach.presentation.ui.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.coach.domain.JoinCode
import com.ricardofrango.squadify.coach.domain.JoinWithCodeUseCase
import com.ricardofrango.squadify.data.remote.ApiException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JoinWithCodeUiState(
    val code: String = "",
    val isSubmitting: Boolean = false,
    val refusal: JoinCode.Refusal? = null
) {
    val canJoin: Boolean get() = JoinCode.isComplete(code) && !isSubmitting
}

class JoinWithCodeViewModel(private val joinWithCodeUseCase: JoinWithCodeUseCase) : ViewModel() {
    private val _state = MutableStateFlow(JoinWithCodeUiState())
    val state: StateFlow<JoinWithCodeUiState> = _state.asStateFlow()

    // Joined: back to the home, which reloads with the new age group.
    private val _joined = MutableSharedFlow<Unit>()
    val joined: SharedFlow<Unit> = _joined.asSharedFlow()

    fun onCodeChanged(code: String) {
        // Seven characters at most: the six of the code and its dash.
        _state.update { it.copy(code = code.take(JoinCode.LENGTH + 1), refusal = null) }
    }

    fun onJoinClicked() {
        if (!_state.value.canJoin) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, refusal = null) }
            try {
                joinWithCodeUseCase(_state.value.code)
                _joined.emit(Unit)
            } catch (e: Exception) {
                _state.update { it.copy(isSubmitting = false, refusal = JoinCode.refusal((e as? ApiException)?.statusCode)) }
            }
        }
    }
}
