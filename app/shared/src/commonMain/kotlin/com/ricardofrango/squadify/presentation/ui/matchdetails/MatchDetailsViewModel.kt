package com.ricardofrango.squadify.presentation.ui.matchdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.DeleteMatchUseCase
import com.ricardofrango.squadify.domain.usecase.GetMatchDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.ManageCallUpsUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateMatchUseCase
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchCallUpWithPlayer
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.UpdateMatchRequest
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_failed_to_load_match
import squadify.app.shared.generated.resources.msg_match_change_failed
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
    private val deleteMatchUseCase: DeleteMatchUseCase,
    private val manageCallUpsUseCase: ManageCallUpsUseCase
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
                if (details.match.status == MatchStatus.LIVE || details.match.status == MatchStatus.HALF_TIME) {
                    // This screen isn't where a live match should be viewed -- hand off immediately
                    // instead of rendering the (now stale-looking) details view.
                    _navEvent.emit(MatchDetailsNavEvent.RedirectToLiveMatch(matchId))
                    return@launch
                }
                _state.update { current ->
                    MatchDetailsUiState.Idle(
                        details = details,
                        sort = (current as? MatchDetailsUiState.Idle)?.sort ?: CallUpSort.POSITION
                    )
                }
            } catch (e: Exception) {
                _state.update { MatchDetailsUiState.Error(getString(Res.string.msg_failed_to_load_match)) }
            }
        }
    }

    fun onSortSelected(sort: CallUpSort) = updateIdle { it.copy(sort = sort) }

    fun clearTransientError() = updateIdle { it.copy(transientError = null) }

    /** Coach or staff adds a pool player to the call-up (batch replace under the hood). */
    fun onAddToCallUp(player: Player) {
        val current = idle() ?: return
        if (!current.details.canManageMatchDay) return

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

    /** Coach or staff removes an already-called-up player (batch replace under the hood, same as
     * [onAddToCallUp] in reverse). */
    fun onRemoveFromCallUp(callUp: MatchCallUpWithPlayer) {
        val current = idle() ?: return
        if (!current.details.canManageMatchDay) return

        _state.update {
            current.copy(details = current.details.copy(
                callUps = current.details.callUps.filterNot { it.callUp.id == callUp.callUp.id },
                poolPlayers = (current.details.poolPlayers + callUp.player).distinctBy { it.id }
            ))
        }
        viewModelScope.launch {
            try {
                val playerIds = current.details.callUps.map { it.player.id }.filterNot { it == callUp.player.id }
                manageCallUpsUseCase(matchId, playerIds)
                load(matchId, forceReload = true) // pull back the real call-up ids
            } catch (e: Exception) {
                _state.update {
                    (it as? MatchDetailsUiState.Idle)?.copy(
                        details = it.details.copy(
                            callUps = (it.details.callUps + callUp).distinctBy { cu -> cu.callUp.id },
                            poolPlayers = it.details.poolPlayers.filterNot { p -> p.id == callUp.player.id }
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

    /** Permanently removes the match -- coach-only, same gate as [onEditMatch]. Not optimistic
     * (unlike the call-up/edit actions above): there's no sensible local state to show once the
     * match itself is gone, so this waits for the server before navigating away. */
    fun onDeleteMatch() {
        val current = idle() ?: return
        if (!current.details.canManage) return
        viewModelScope.launch {
            _state.update { current.copy(isSubmitting = true, transientError = null) }
            try {
                deleteMatchUseCase(matchId)
                _navEvent.emit(MatchDetailsNavEvent.MatchDeleted)
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

    private fun idle() = _state.value as? MatchDetailsUiState.Idle

    private inline fun updateIdle(block: (MatchDetailsUiState.Idle) -> MatchDetailsUiState.Idle) {
        _state.update { if (it is MatchDetailsUiState.Idle) block(it) else it }
    }
}
