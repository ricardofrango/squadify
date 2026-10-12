package com.ricardofrango.squadify.presentation.ui.opponents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetOpponentDetailsUseCase
import com.ricardofrango.squadify.model.OpponentDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

sealed class OpponentDetailsUiState {
    data object Loading : OpponentDetailsUiState()
    data object Error : OpponentDetailsUiState()

    /** [seasonId]: the head-to-head's season. */
    data class Idle(val details: OpponentDetails, val seasonId: String?) : OpponentDetailsUiState()
}

/** Opponent details (design 38f). */
class OpponentDetailsViewModel(
    private val getOpponentDetailsUseCase: GetOpponentDetailsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<OpponentDetailsUiState>(OpponentDetailsUiState.Loading)
    val state: StateFlow<OpponentDetailsUiState> = _state.asStateFlow()

    /** [force] reloads after an edit, keeping the chosen season. */
    fun load(opponentId: String, force: Boolean = false) {
        if (!force && _state.value is OpponentDetailsUiState.Idle) return
        viewModelScope.launch {
            val previous = _state.value as? OpponentDetailsUiState.Idle
            if (previous == null) _state.update { OpponentDetailsUiState.Loading }
            _state.update {
                try {
                    val details = getOpponentDetailsUseCase(opponentId)
                    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                    OpponentDetailsUiState.Idle(details, previous?.seasonId ?: defaultHeadToHeadSeasonId(details.seasons, today))
                } catch (e: Exception) {
                    previous ?: OpponentDetailsUiState.Error
                }
            }
        }
    }

    fun onSeasonSelected(seasonId: String) = _state.update {
        if (it is OpponentDetailsUiState.Idle) it.copy(seasonId = seasonId) else it
    }
}
