package com.ricardofrango.squadify.presentation.ui.matchrecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetMatchDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.SaveMatchRecordUseCase
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.presentation.ui.livematch.teamSize
import com.ricardofrango.squadify.presentation.errorMessage
import com.ricardofrango.squadify.presentation.ui.matchdetails.CallUpSort
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_failed_to_load_match
import squadify.app.shared.generated.resources.msg_record_save_failed
import kotlin.uuid.Uuid

sealed class MatchRecordUiState {
    data object Loading : MatchRecordUiState()
    data class Error(val message: String) : MatchRecordUiState()

    /**
     * [events] the goals and cards on screen; [halfLength], [teamSize] (how many a side) and [minutes]
     * (player id -> what's typed) only matter for a game entered afterwards, [sort] is the minutes
     * list's order. Nothing is sent until Save.
     */
    data class Idle(
        val details: MatchDetails,
        val events: List<DraftEvent>,
        val halfLength: Int,
        val minutes: Map<String, String>,
        val teamSize: Int = details.teamSize,
        val sort: CallUpSort = DEFAULT_MINUTES_SORT,
        val saving: Boolean = false,
        val saveError: String? = null,
        val dirty: Boolean = false
    ) : MatchRecordUiState() {
        val afterGame: Boolean get() = details.isAfterGame
        val matchLength: Int get() = halfLength * details.match.halves

        // Who was on the pitch in a game followed live, from its stats (they can't change).
        private val playedLive: Set<String>
            get() = details.playerStats.filter { it.started || it.minutesPlayed > 0 }.map { it.playerId }.toSet() +
                details.events.filter { it.type == EventType.SUBSTITUTION_IN }.mapNotNull { it.playerId }

        val problem: RecordProblem?
            get() = recordProblem(afterGame, events, minutes, matchLength, playedLive)

        /** Who can score or assist: who played, or everyone called up while minutes are still blank. */
        val scorers: Set<String>
            get() = if (afterGame) {
                val typed = minutes.filterValues { (it.trim().toIntOrNull() ?: 0) > 0 }.keys
                typed.ifEmpty { details.callUps.map { it.player.id }.toSet() }
            } else {
                playedLive.ifEmpty { details.callUps.map { it.player.id }.toSet() }
            }

        // A first entry can always be saved (it finishes the game); a correction once something changed.
        val canSave: Boolean get() = !saving && problem == null && (dirty || details.match.status == MatchStatus.SCHEDULED)
    }
}

/** The editor of a match's record after the game (MatchRecordLogic.kt). */
class MatchRecordViewModel(
    private val getMatchDetailsUseCase: GetMatchDetailsUseCase,
    private val saveMatchRecordUseCase: SaveMatchRecordUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<MatchRecordUiState>(MatchRecordUiState.Loading)
    val state: StateFlow<MatchRecordUiState> = _state.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    fun load(matchId: String) {
        if (_state.value is MatchRecordUiState.Idle) return
        viewModelScope.launch {
            _state.value = MatchRecordUiState.Loading
            try {
                val details = getMatchDetailsUseCase(matchId)
                val minutes = details.callUps.associate { callUp ->
                    callUp.player.id to (details.playerStats.firstOrNull { it.playerId == callUp.player.id }?.minutesPlayed?.takeIf { it > 0 }?.toString() ?: "")
                }
                _state.value = MatchRecordUiState.Idle(
                    details = details,
                    events = draftEvents(details.events),
                    halfLength = details.match.halfLengthMinutes ?: DEFAULT_HALF_LENGTH,
                    minutes = minutes
                )
            } catch (e: Exception) {
                _state.value = MatchRecordUiState.Error(errorMessage(e, Res.string.msg_failed_to_load_match))
            }
        }
    }

    /** Adds [event] (a blank [DraftEvent.key]) or replaces the one with its key. */
    fun onSaveEvent(event: DraftEvent) = edit { state ->
        val keyed = if (event.key.isEmpty()) event.copy(key = Uuid.random().toString()) else event
        val events = if (state.events.any { it.key == keyed.key }) state.events.map { if (it.key == keyed.key) keyed else it } else state.events + keyed
        state.copy(events = events.sortedWith(compareBy({ it.period }, { it.minute })))
    }

    fun onDeleteEvent(key: String) = edit { it.copy(events = it.events.filterNot { e -> e.key == key }) }

    fun onHalfLength(minutes: Int) = edit { it.copy(halfLength = minutes) }

    fun onTeamSize(size: Int) = edit { it.copy(teamSize = size) }

    fun onMinutes(playerId: String, text: String) = edit {
        it.copy(minutes = it.minutes + (playerId to text.filter(Char::isDigit).take(3)))
    }

    /** The minutes list's order; not a change to the record. */
    fun onSort(sort: CallUpSort) = _state.update { if (it is MatchRecordUiState.Idle) it.copy(sort = sort) else it }

    /** Every called-up player's minutes set to the whole match (most of a team often plays it all). */
    fun onFullMatch(playerId: String) = edit { it.copy(minutes = it.minutes + (playerId to it.matchLength.toString())) }

    fun save() {
        val current = _state.value as? MatchRecordUiState.Idle ?: return
        if (!current.canSave) return
        viewModelScope.launch {
            _state.value = current.copy(saving = true, saveError = null)
            try {
                saveMatchRecordUseCase(
                    current.details.match.id,
                    recordRequest(current.afterGame, current.events, current.halfLength, current.minutes, current.teamSize)
                )
                _state.value = current.copy(saving = false, dirty = false)
                _saved.emit(Unit)
            } catch (e: Exception) {
                _state.value = current.copy(saving = false, saveError = errorMessage(e, Res.string.msg_record_save_failed))
            }
        }
    }

    private inline fun edit(block: (MatchRecordUiState.Idle) -> MatchRecordUiState.Idle) {
        _state.update { if (it is MatchRecordUiState.Idle && !it.saving) block(it).copy(dirty = true, saveError = null) else it }
    }
}
