package com.ricardofrango.squadify.presentation.ui.opponents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetOpponentListUseCase
import com.ricardofrango.squadify.model.OpponentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class OpponentsUiState {
    data object Loading : OpponentsUiState()
    data object Error : OpponentsUiState()
    data class Idle(val list: OpponentList) : OpponentsUiState()
}

/** The Opponents screen (design 38), in either mode -- the mode only changes the screen, not the data. */
class OpponentsViewModel(
    private val getOpponentListUseCase: GetOpponentListUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<OpponentsUiState>(OpponentsUiState.Loading)
    val state: StateFlow<OpponentsUiState> = _state.asStateFlow()

    // Kept outside the state so it survives a reload and stays typed while loading or on an error.
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** [force] reloads after returning from a screen that changed opponents. */
    fun load(clubId: String, ageGroupId: String?, force: Boolean = false) {
        if (!force && _state.value is OpponentsUiState.Idle) return
        viewModelScope.launch {
            if (_state.value !is OpponentsUiState.Idle) _state.update { OpponentsUiState.Loading }
            _state.update {
                try {
                    OpponentsUiState.Idle(getOpponentListUseCase(clubId, ageGroupId))
                } catch (e: Exception) {
                    OpponentsUiState.Error
                }
            }
        }
    }

    fun onQueryChange(query: String) = _query.update { query }
}
