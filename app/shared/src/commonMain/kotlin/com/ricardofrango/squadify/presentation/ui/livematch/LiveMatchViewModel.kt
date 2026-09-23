package com.ricardofrango.squadify.presentation.ui.livematch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.domain.usecase.AddMatchEventUseCase
import com.ricardofrango.squadify.domain.usecase.DeleteMatchEventUseCase
import com.ricardofrango.squadify.domain.usecase.GetMatchDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetMatchEventsUseCase
import com.ricardofrango.squadify.domain.usecase.GetMatchScoreUseCase
import com.ricardofrango.squadify.domain.usecase.ObserveLiveMatchUseCase
import com.ricardofrango.squadify.domain.usecase.PauseMatchUseCase
import com.ricardofrango.squadify.domain.usecase.ResumeMatchUseCase
import com.ricardofrango.squadify.domain.usecase.SetMatchMinuteUseCase
import com.ricardofrango.squadify.domain.usecase.StartMatchUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateMatchStatusUseCase
import com.ricardofrango.squadify.model.CreateMatchEventRequest
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.StartMatchRequest
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_failed_to_load_match
import squadify.app.shared.generated.resources.msg_live_match_action_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock

/**
 * Backs the whole live match console (LiveMatchScreen): pre-kickoff line-up picker, the live
 * squad/events tabs, and the full-time summary -- one [LiveMatchUiState.Idle] covers all of them,
 * branching on `details.match.status`.
 *
 * Every mutating action here updates local state from its own REST response immediately (so the
 * user driving the action never waits on the WebSocket), while [observeLiveMatchUseCase] is a second,
 * best-effort listener that folds in changes made elsewhere (another coach's phone, the club office).
 * A dropped socket is not retried -- see PROCESS_FLOWS.md's live match console notes -- REST calls
 * keep working regardless, just without cross-device echo until the screen is reopened.
 */
class LiveMatchViewModel(
    private val getMatchDetailsUseCase: GetMatchDetailsUseCase,
    private val getMatchEventsUseCase: GetMatchEventsUseCase,
    private val getMatchScoreUseCase: GetMatchScoreUseCase,
    private val startMatchUseCase: StartMatchUseCase,
    private val updateMatchStatusUseCase: UpdateMatchStatusUseCase,
    private val pauseMatchUseCase: PauseMatchUseCase,
    private val resumeMatchUseCase: ResumeMatchUseCase,
    private val setMatchMinuteUseCase: SetMatchMinuteUseCase,
    private val addMatchEventUseCase: AddMatchEventUseCase,
    private val deleteMatchEventUseCase: DeleteMatchEventUseCase,
    private val observeLiveMatchUseCase: ObserveLiveMatchUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<LiveMatchUiState>(LiveMatchUiState.Loading)
    val state: StateFlow<LiveMatchUiState> = _state.asStateFlow()

    private var matchId: String = ""
    private var subscribed = false

    fun load(matchId: String) {
        this.matchId = matchId
        viewModelScope.launch {
            _state.update { LiveMatchUiState.Loading }
            try {
                val details = getMatchDetailsUseCase(matchId)
                val started = details.match.status != MatchStatus.SCHEDULED
                val events = if (started) getMatchEventsUseCase(matchId) else emptyList()
                val score = if (started) getMatchScoreUseCase(matchId) else MatchScore(matchId, 0, 0)
                _state.update { LiveMatchUiState.Idle(details = details, events = events, score = score) }
                subscribeToLiveUpdates()
            } catch (e: Exception) {
                _state.update { LiveMatchUiState.Error(getString(Res.string.msg_failed_to_load_match)) }
            }
        }
    }

    private fun subscribeToLiveUpdates() {
        if (subscribed) return
        subscribed = true
        viewModelScope.launch {
            try {
                observeLiveMatchUseCase(matchId).collect { update ->
                    _state.update { state ->
                        if (state !is LiveMatchUiState.Idle) return@update state
                        var next = state.copy(score = update.score)
                        update.deletedEventId?.let { id ->
                            next = next.copy(events = next.events.filterNot { it.id == id })
                        }
                        update.event?.let { event ->
                            if (next.events.none { it.id == event.id }) {
                                next = next.copy(events = (next.events + event).sortedBy { it.minute })
                            }
                        }
                        update.match?.let { match ->
                            next = next.copy(details = next.details.copy(match = match))
                        }
                        next
                    }
                }
            } catch (e: Exception) {
                // Socket dropped -- the REST-driven actions below keep working; see class doc.
            } finally {
                subscribed = false
            }
        }
    }

    fun onTabSelected(tab: LiveMatchTab) {
        _state.update { if (it is LiveMatchUiState.Idle) it.copy(selectedTab = tab) else it }
    }

    fun clearError() {
        _state.update { if (it is LiveMatchUiState.Idle) it.copy(error = null) else it }
    }

    fun onKickOff(startingPlayerIds: List<String>, halfLengthMinutes: Int) = runSubmitting { current ->
        startMatchUseCase(matchId, StartMatchRequest(startingPlayerIds, halfLengthMinutes))
        val details = getMatchDetailsUseCase(matchId)
        subscribeToLiveUpdates()
        current.copy(details = details, events = emptyList(), score = MatchScore(matchId, 0, 0), isSubmitting = false)
    }

    fun onHalfTime() = runSubmitting { current ->
        updateMatchStatusUseCase(matchId, MatchStatus.HALF_TIME)
        current.copy(
            details = current.details.copy(match = current.details.match.copy(status = MatchStatus.HALF_TIME)),
            isSubmitting = false
        )
    }

    fun onResumeSecondHalf() = runSubmitting { current ->
        updateMatchStatusUseCase(matchId, MatchStatus.LIVE)
        // Mirrors what the server just persisted (UpdateMatchStatusUseCase sets this on the same
        // HALF_TIME -> LIVE transition) so the running clock picks it up immediately rather than
        // waiting on a refetch or the WebSocket echo.
        current.copy(
            details = current.details.copy(
                match = current.details.match.copy(status = MatchStatus.LIVE, secondHalfStartedAt = Clock.System.now())
            ),
            isSubmitting = false
        )
    }

    fun onFullTime() = runSubmitting { current ->
        updateMatchStatusUseCase(matchId, MatchStatus.FINISHED)
        current.copy(
            details = current.details.copy(match = current.details.match.copy(status = MatchStatus.FINISHED)),
            isSubmitting = false
        )
    }

    /**
     * Freezes the running clock for a mid-half stoppage -- distinct from [onHalfTime], which is a
     * proper status transition. Refetches rather than computing the new state locally: the server is
     * the sole authority on match time (both here and for [onResumeClock] below), so there's no local
     * clock-skew risk from trusting this device's own `now()` for either the pause instant or the
     * resume-time shift math (see PauseMatchUseCase/ResumeMatchUseCase).
     */
    fun onPauseClock() = runSubmitting { current ->
        pauseMatchUseCase(matchId)
        current.copy(details = getMatchDetailsUseCase(matchId), isSubmitting = false)
    }

    fun onResumeClock() = runSubmitting { current ->
        resumeMatchUseCase(matchId)
        current.copy(details = getMatchDetailsUseCase(matchId), isSubmitting = false)
    }

    /**
     * A manual correction to the running clock -- server-persisted (unlike the local-only "nudge on
     * top of the derived value" this replaced, which never reached the server, the club Matches list,
     * or any other device watching the same match). Refetches for the same reason as [onPauseClock]/
     * [onResumeClock]: the server computes the actual shift, this device's own clock is never trusted.
     */
    fun onSetMinute(minute: Int) = runSubmitting { current ->
        setMatchMinuteUseCase(matchId, minute)
        current.copy(details = getMatchDetailsUseCase(matchId), isSubmitting = false)
    }

    /** Our score box (unattributed) or the opponent's -- see LiveMatchScreen's score row. */
    fun onLogGoal(minute: Int, isOpponent: Boolean) = addEvent(CreateMatchEventRequest(EventType.GOAL, minute, isOpponent = isOpponent))

    fun onLogEvent(type: EventType, playerId: String, minute: Int) = addEvent(CreateMatchEventRequest(type, minute, playerId = playerId))

    fun onSubstitute(outPlayerId: String, inPlayerId: String, minute: Int) = runSubmitting { current ->
        val outEvent = addMatchEventUseCase(matchId, CreateMatchEventRequest(EventType.SUBSTITUTION_OUT, minute, playerId = outPlayerId))
        val inEvent = addMatchEventUseCase(matchId, CreateMatchEventRequest(EventType.SUBSTITUTION_IN, minute, playerId = inPlayerId))
        current.copy(events = (current.events + outEvent + inEvent).sortedBy { it.minute }, isSubmitting = false)
    }

    fun onDeleteEvent(eventId: String) = runSubmitting { current ->
        deleteMatchEventUseCase(matchId, eventId)
        val score = getMatchScoreUseCase(matchId)
        current.copy(events = current.events.filterNot { it.id == eventId }, score = score, isSubmitting = false)
    }

    /** Deletes both halves of a logged substitution together -- see LiveMatchScreen's paired row. */
    fun onDeleteSubstitution(outEventId: String, inEventId: String) = runSubmitting { current ->
        deleteMatchEventUseCase(matchId, outEventId)
        deleteMatchEventUseCase(matchId, inEventId)
        current.copy(events = current.events.filterNot { it.id == outEventId || it.id == inEventId }, isSubmitting = false)
    }

    private fun addEvent(request: CreateMatchEventRequest) = runSubmitting { current ->
        val event = addMatchEventUseCase(matchId, request)
        val score = getMatchScoreUseCase(matchId)
        current.copy(events = (current.events + event).sortedBy { it.minute }, score = score, isSubmitting = false)
    }

    private fun runSubmitting(block: suspend (LiveMatchUiState.Idle) -> LiveMatchUiState.Idle) {
        val current = _state.value as? LiveMatchUiState.Idle ?: return
        _state.update { current.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                // block() makes real network calls (POST/DELETE) -- it must run exactly once. Calling
                // it directly inside _state.update {} is wrong: update() is a compare-and-swap retry
                // loop, and any concurrent write to _state (e.g. subscribeToLiveUpdates' WebSocket
                // handler firing for this same call's own broadcast) makes it retry the lambda, which
                // would re-run block() and re-send the request -- this is what caused one tap to fire a
                // burst of duplicate POSTs. Compute the result once, then apply it as a plain value.
                val result = block(current)
                _state.update { result }
            } catch (e: ApiException) {
                _state.update { current.copy(isSubmitting = false, error = getString(Res.string.msg_live_match_action_failed)) }
            } catch (e: Exception) {
                _state.update { current.copy(isSubmitting = false, error = getString(Res.string.msg_live_match_action_failed)) }
            }
        }
    }
}
