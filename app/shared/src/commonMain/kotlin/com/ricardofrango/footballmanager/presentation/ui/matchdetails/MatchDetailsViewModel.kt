package com.ricardofrango.footballmanager.presentation.ui.matchdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.footballmanager.domain.usecase.GetMatchDetailsUseCase
import com.ricardofrango.footballmanager.domain.usecase.ManageCallUpsUseCase
import com.ricardofrango.footballmanager.domain.usecase.UpdateCallUpStatusUseCase
import com.ricardofrango.footballmanager.domain.usecase.UpdateMatchUseCase
import com.ricardofrango.footballmanager.model.CallUpStatus
import com.ricardofrango.footballmanager.model.MatchCallUp
import com.ricardofrango.footballmanager.model.MatchCallUpWithPlayer
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.UpdateCallUpRequest
import com.ricardofrango.footballmanager.model.UpdateMatchRequest
import footballmanager.app.shared.generated.resources.Res
import footballmanager.app.shared.generated.resources.msg_failed_to_load_match
import footballmanager.app.shared.generated.resources.msg_match_change_failed
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class MatchDetailsViewModel(
    private val getMatchDetailsUseCase: GetMatchDetailsUseCase,
    private val updateMatchUseCase: UpdateMatchUseCase,
    private val manageCallUpsUseCase: ManageCallUpsUseCase,
    private val updateCallUpStatusUseCase: UpdateCallUpStatusUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<MatchDetailsUiState>(MatchDetailsUiState.Loading)
    val state: StateFlow<MatchDetailsUiState> = _state.asStateFlow()

    private val _navEvent = MutableSharedFlow<MatchDetailsNavEvent>()
    val navEvent: SharedFlow<MatchDetailsNavEvent> = _navEvent.asSharedFlow()

    private var matchId: String = ""

    fun load(matchId: String, forceReload: Boolean = false) {
        this.matchId = matchId
        if (!forceReload && _state.value is MatchDetailsUiState.Idle) return

        viewModelScope.launch {
            if (_state.value !is MatchDetailsUiState.Idle) _state.update { MatchDetailsUiState.Loading }
            try {
                val details = getMatchDetailsUseCase(matchId)
                _state.update { current ->
                    MatchDetailsUiState.Idle(
                        details = details,
                        sort = (current as? MatchDetailsUiState.Idle)?.sort ?: CallUpSort.STATUS
                    )
                }
            } catch (e: Exception) {
                _state.update { MatchDetailsUiState.Error(getString(Res.string.msg_failed_to_load_match)) }
            }
        }
    }

    fun onSortSelected(sort: CallUpSort) = updateIdle { it.copy(sort = sort) }

    fun clearTransientError() = updateIdle { it.copy(transientError = null) }

    /** Coach taps a called-up player's status chip to cycle it -- applied optimistically. */
    fun onCycleCallUpStatus(callUp: MatchCallUpWithPlayer) {
        val current = idle() ?: return
        if (!current.details.canManage) return
        val next = callUp.callUp.status.next()

        _state.update {
            current.copy(details = current.details.copy(
                callUps = current.details.callUps.map {
                    if (it.callUp.id == callUp.callUp.id) it.copy(callUp = it.callUp.copy(status = next)) else it
                }
            ))
        }
        viewModelScope.launch {
            try {
                updateCallUpStatusUseCase(callUp.callUp.id, UpdateCallUpRequest(status = next))
            } catch (e: Exception) {
                _state.update {
                    (it as? MatchDetailsUiState.Idle)?.copy(
                        details = it.details.copy(
                            callUps = it.details.callUps.map { cu ->
                                if (cu.callUp.id == callUp.callUp.id) callUp else cu
                            }
                        ),
                        transientError = getString(Res.string.msg_match_change_failed)
                    ) ?: it
                }
            }
        }
    }

    /** Coach adds a pool player to the call-up (batch replace under the hood). */
    fun onAddToCallUp(player: Player) {
        val current = idle() ?: return
        if (!current.details.canManage) return

        val optimistic = MatchCallUpWithPlayer(
            callUp = MatchCallUp(id = "pending-${player.id}", matchId = matchId, playerId = player.id, status = CallUpStatus.CALLED_UP),
            player = player
        )
        _state.update {
            current.copy(details = current.details.copy(
                callUps = current.details.callUps + optimistic,
                poolPlayers = current.details.poolPlayers.filterNot { it.id == player.id }
            ))
        }
        viewModelScope.launch {
            try {
                val playerIds = (current.details.callUps.map { it.player.id } + player.id).distinct()
                manageCallUpsUseCase(matchId, playerIds)
                load(matchId, forceReload = true) // pull back the real call-up ids
            } catch (e: Exception) {
                _state.update {
                    (it as? MatchDetailsUiState.Idle)?.copy(
                        details = it.details.copy(
                            callUps = it.details.callUps.filterNot { cu -> cu.callUp.id == optimistic.callUp.id },
                            poolPlayers = (it.details.poolPlayers + player).distinctBy { p -> p.id }
                        ),
                        transientError = getString(Res.string.msg_match_change_failed)
                    ) ?: it
                }
            }
        }
    }

    fun onEditMatch(request: UpdateMatchRequest) {
        val current = idle() ?: return
        if (!current.details.canManage) return
        viewModelScope.launch {
            _state.update { current.copy(isSubmitting = true, transientError = null) }
            try {
                updateMatchUseCase(matchId, request)
                load(matchId, forceReload = true)
            } catch (e: Exception) {
                _state.update {
                    (it as? MatchDetailsUiState.Idle)?.copy(
                        isSubmitting = false,
                        transientError = getString(Res.string.msg_match_change_failed)
                    ) ?: it
                }
            }
        }
    }

    /**
     * The bottom button just opens the live match screen -- starting the game, substitutions, events
     * etc. all live there. It does not change the match status.
     */
    fun onEnterLiveMatch() {
        viewModelScope.launch { _navEvent.emit(MatchDetailsNavEvent.EnterLiveMatch(matchId)) }
    }

    private fun idle() = _state.value as? MatchDetailsUiState.Idle

    private inline fun updateIdle(block: (MatchDetailsUiState.Idle) -> MatchDetailsUiState.Idle) {
        _state.update { if (it is MatchDetailsUiState.Idle) block(it) else it }
    }

    private fun CallUpStatus.next(): CallUpStatus = when (this) {
        CallUpStatus.CALLED_UP -> CallUpStatus.CONFIRMED
        CallUpStatus.CONFIRMED -> CallUpStatus.DECLINED
        CallUpStatus.DECLINED -> CallUpStatus.NOT_AVAILABLE
        CallUpStatus.NOT_AVAILABLE -> CallUpStatus.STANDBY
        CallUpStatus.STANDBY -> CallUpStatus.CALLED_UP
    }
}
