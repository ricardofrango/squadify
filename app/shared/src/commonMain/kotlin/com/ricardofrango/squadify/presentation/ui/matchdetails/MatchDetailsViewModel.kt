package com.ricardofrango.squadify.presentation.ui.matchdetails

import kotlinx.coroutines.CancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.DeleteMatchUseCase
import com.ricardofrango.squadify.domain.usecase.GetMatchDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.ManageCallUpsUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateMatchUseCase
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.UpdateMatchRequest
import com.ricardofrango.squadify.presentation.errorMessage
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_failed_to_load_match
import squadify.app.shared.generated.resources.msg_callup_save_failed
import squadify.app.shared.generated.resources.msg_match_change_failed
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    // Pull-to-refresh: the screen stays while it reloads; a failure is a one-off message.
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _refreshFailed = MutableSharedFlow<Unit>()
    val refreshFailed: SharedFlow<Unit> = _refreshFailed.asSharedFlow()

    /**
     * Pull-to-refresh: the match again with the screen left as it is (and a call-up being picked kept,
     * as on return). If it has kicked off meanwhile, on to the live game. A failure keeps what's shown.
     */
    fun refresh() {
        if (_state.value !is MatchDetailsUiState.Idle || _isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val details = getMatchDetailsUseCase(matchId)
                if (details.match.status == MatchStatus.LIVE || details.match.status == MatchStatus.HALF_TIME) {
                    _navEvent.emit(MatchDetailsNavEvent.RedirectToLiveMatch(matchId))
                } else {
                    _state.update { current ->
                        val previous = current as? MatchDetailsUiState.Idle ?: return@update current
                        MatchDetailsUiState.Idle(details = details, sort = previous.sort).keepingDraftOf(previous)
                    }
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
                    val previous = current as? MatchDetailsUiState.Idle
                    MatchDetailsUiState.Idle(details = details, sort = previous?.sort ?: CallUpSort.POSITION)
                        .keepingDraftOf(previous)
                }
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_failed_to_load_match)
                _state.update { MatchDetailsUiState.Error(message) }
            }
        }
    }

    fun onSortSelected(sort: CallUpSort) = updateIdle { it.copy(sort = sort) }

    fun clearTransientError() = updateIdle { it.copy(transientError = null) }

    /** Adds a player to the call-up on screen; nothing is sent until [onSaveCallUp]. */
    fun onAddToCallUp(player: Player) = updateIdle {
        if (it.details.canManageCallUps) it.copy(selectedIds = it.selectedIds + player.id, transientError = null) else it
    }

    /** Takes a player off the call-up on screen; nothing is sent until [onSaveCallUp]. */
    fun onRemoveFromCallUp(player: Player) = updateIdle {
        if (it.details.canManageCallUps) it.copy(selectedIds = it.selectedIds - player.id, transientError = null) else it
    }

    /**
     * Sends the call-up on screen (the server replaces the saved one with it), then reloads, which
     * makes it the saved call-up. If it fails, the draft stays on screen with the error, to try again.
     */
    fun onSaveCallUp() {
        val current = idle() ?: return
        if (!current.details.canManageCallUps || current.isSavingCallUp) return
        if (current.selectedIds.size < current.details.minimumCallUp()) return
        viewModelScope.launch {
            _state.update { current.copy(isSavingCallUp = true, transientError = null) }
            try {
                manageCallUpsUseCase(matchId, current.selectedIds.toList())
                load(matchId, forceReload = true)
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_callup_save_failed)
                updateIdle { it.copy(isSavingCallUp = false, transientError = message) }
            }
        }
    }

    fun onEditMatch(request: UpdateMatchRequest) {
        val current = idle() ?: return
        if (!current.details.canManageMatch) return
        viewModelScope.launch {
            _state.update { current.copy(isUpdatingMatch = true, transientError = null) }
            try {
                updateMatchUseCase(matchId, request)
                load(matchId, forceReload = true)
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_match_change_failed)
                _state.update {
                    (it as? MatchDetailsUiState.Idle)?.copy(
                        isUpdatingMatch = false,
                        transientError = message
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
        if (!current.details.canManageMatch) return
        viewModelScope.launch {
            _state.update { current.copy(isUpdatingMatch = true, transientError = null) }
            try {
                deleteMatchUseCase(matchId)
                _navEvent.emit(MatchDetailsNavEvent.MatchDeleted)
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_match_change_failed)
                _state.update {
                    (it as? MatchDetailsUiState.Idle)?.copy(
                        isUpdatingMatch = false,
                        transientError = message
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
