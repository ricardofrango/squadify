package com.ricardofrango.squadify.presentation.ui.opponents

import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.CancellationException
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

    // Pull-to-refresh: the screen stays while it reloads; a failure is a one-off message.
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _refreshFailed = MutableSharedFlow<Unit>()
    val refreshFailed: SharedFlow<Unit> = _refreshFailed.asSharedFlow()

    /** Pull-to-refresh: the opponent again with the screen left as it is, keeping the chosen season. */
    fun refresh(opponentId: String) {
        val previous = _state.value as? OpponentDetailsUiState.Idle ?: return
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val details = getOpponentDetailsUseCase(opponentId)
                _state.update { current ->
                    val seasonId = (current as? OpponentDetailsUiState.Idle)?.seasonId ?: previous.seasonId
                    OpponentDetailsUiState.Idle(details, seasonId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _refreshFailed.emit(Unit)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

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
