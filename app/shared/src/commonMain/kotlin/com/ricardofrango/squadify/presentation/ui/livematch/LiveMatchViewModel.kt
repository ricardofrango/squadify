package com.ricardofrango.squadify.presentation.ui.livematch

import com.ricardofrango.squadify.model.LiveMatchUpdate
import com.ricardofrango.squadify.domain.service.LiveFeedEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.AddMatchEventUseCase
import com.ricardofrango.squadify.domain.usecase.DeleteMatchEventUseCase
import com.ricardofrango.squadify.domain.usecase.GetMatchDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetMatchEventsUseCase
import com.ricardofrango.squadify.domain.usecase.GetMatchScoreUseCase
import com.ricardofrango.squadify.domain.usecase.ObserveLiveMatchUseCase
import com.ricardofrango.squadify.domain.usecase.PauseMatchUseCase
import com.ricardofrango.squadify.domain.usecase.ResumeMatchUseCase
import com.ricardofrango.squadify.domain.usecase.SetMatchMinuteUseCase
import com.ricardofrango.squadify.domain.usecase.SetMatchLineupUseCase
import com.ricardofrango.squadify.domain.usecase.StartMatchUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateMatchStatusUseCase
import com.ricardofrango.squadify.model.CreateMatchEventRequest
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.estimateServerClockOffset
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.StartMatchRequest
import com.ricardofrango.squadify.model.MatchLineup
import com.ricardofrango.squadify.model.SetMatchLineupRequest
import com.ricardofrango.squadify.presentation.errorMessage
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_failed_to_load_match
import squadify.app.shared.generated.resources.msg_live_match_action_failed
import com.ricardofrango.squadify.model.MatchEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Backs the whole live match console (LiveMatchScreen): pre-kickoff line-up picker, the live
 * squad/events tabs, and the full-time summary -- one [LiveMatchUiState.Idle] covers all of them,
 * branching on `details.match.status`.
 *
 * Every mutating action here updates local state from its own REST response immediately (so the
 * user driving the action never waits on the WebSocket), while [observeLiveMatchUseCase] is a second
 * listener that folds in changes made elsewhere (another coach's phone, the club office, the person
 * running the game while this phone only watches). The socket reopens itself when it drops (see
 * `keepConnected`), and each time it's back the match, events and score are reloaded, since whatever
 * was sent meanwhile was missed ([reloadAfterReconnect]); [LiveMatchUiState.Idle.reconnecting] shows
 * while it's down. The screen closes it while the app is in the background and reopens it on return
 * ([onBackground], [onForeground]). REST calls keep working regardless.
 *
 * Quick actions in a row stay in step (a de-sync seen in a real game, 2026-10-24: a swap made before
 * the previous one's reply came back started moving the wrong players):
 * - **Lineup saves go one at a time, in order.** Taps made while one is saving collapse into the
 *   latest arrangement, sent when it returns ([drainLineups]).
 * - **Our own echo can't roll the pitch back.** While a save is pending, a socket lineup that isn't
 *   the one we last sent is ignored ([desiredLineup], [awaitingEcho]).
 * - **Actions run one at a time and apply onto the latest state** ([runSubmitting]): each returns a
 *   change, not a snapshot, so a swap or socket update that landed meanwhile isn't wiped out. They
 *   also wait for pending lineup saves, so the server sees everything in the order it was done.
 * - **A refused save reloads the server's pitch**, rather than restoring an older copy over later taps.
 */
/**
 * What the live console can ask of its ViewModel -- an interface so the console's layout can be
 * previewed with a stand-in that does nothing.
 */
interface LiveMatchActions {
    /** How far the device's clock is from the server's; the running clock counts with it. */
    val clockOffset: StateFlow<Duration>
    fun clearFlash()
    fun onDeleteEvent(eventId: String)
    fun onDeleteSubstitution(outEventId: String, inEventId: String)
    fun onFullTime()
    fun onHalfTime()
    /** Kicks off with the players placed on the pitch, in its formation. */
    fun onKickOff(halfLengthMinutes: Int)
    /**
     * Saves the pitch's formation and who stands in each position (slot -> player id). Before kickoff
     * [teamSize] changes how many a side the match is played; null keeps it.
     */
    fun onSetLineup(formation: String, slots: Map<Int, String>, teamSize: Int? = null)
    fun onSaveLineup(onSaved: () -> Unit)
    fun onLogEvent(type: EventType, playerId: String, minute: Int)
    fun onLogGoal(minute: Int, isOpponent: Boolean)
    fun onLogPlayerGoal(scorerId: String, assistPlayerId: String?, minute: Int)
    fun onPauseClock()
    fun onResumeClock()
    fun onResumeSecondHalf()
    fun onSetMinute(minute: Int)
    fun onSubstitute(outPlayerId: String, inPlayerId: String, minute: Int)
    fun onTabSelected(tab: LiveMatchTab)
}

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
    private val observeLiveMatchUseCase: ObserveLiveMatchUseCase,
    private val setMatchLineupUseCase: SetMatchLineupUseCase
) : ViewModel(), LiveMatchActions {

    private val _state = MutableStateFlow<LiveMatchUiState>(LiveMatchUiState.Loading)
    val state: StateFlow<LiveMatchUiState> = _state.asStateFlow()

    private var matchId: String = ""
    // The live socket, while it's wanted (the game has started) and the app is in the foreground.
    private var feedJob: Job? = null
    private var wantsFeed = false
    // Set when the socket drops or the app goes to the background: the next Connected reloads.
    private var missedUpdates = false

    // The arrangement still to send, the running save loop, and the slots of the last one sent until
    // the socket echoes them back. All touched on the main thread only (viewModelScope).
    private var desiredLineup: MatchLineup? = null
    // A team size picked before kickoff, sent with the next lineup.
    private var desiredTeamSize: Int? = null
    private var lineupJob: Job? = null
    // The last lineup save was refused (Save then stays on the screen).
    private var lineupRefused = false
    private var awaitingEcho: Map<Int, String>? = null

    // One action at a time: two substitutions sent together could interleave their OUT and IN events
    // and be paired the wrong way round (see currentPitchSlots).
    private val submitMutex = Mutex()
    private var submitsInFlight = 0

    // How far the server's clock is ahead of this device's. Every match timestamp is the server's, so
    // the running clock counts with `now + clockOffset` -- otherwise a device a few seconds behind the
    // server shows the second half starting at 29:4x instead of 30:00. Re-measured on every fetch.
    private val _clockOffset = MutableStateFlow(Duration.ZERO)
    override val clockOffset: StateFlow<Duration> = _clockOffset.asStateFlow()

    /** The server's "now", as best this device can tell. */
    private fun serverNow() = Clock.System.now() + _clockOffset.value

    private suspend fun fetchDetails(): MatchDetails {
        val sent = Clock.System.now()
        val details = getMatchDetailsUseCase(matchId)
        details.serverNow?.let { _clockOffset.value = estimateServerClockOffset(it, sent, Clock.System.now()) }
        return details
    }

    fun load(matchId: String) {
        this.matchId = matchId
        viewModelScope.launch {
            _state.update { LiveMatchUiState.Loading }
            try {
                val details = fetchDetails()
                val started = details.match.status != MatchStatus.SCHEDULED
                val events = if (started) getMatchEventsUseCase(matchId) else emptyList()
                val score = if (started) getMatchScoreUseCase(matchId) else MatchScore(matchId, 0, 0)
                _state.update { LiveMatchUiState.Idle(details = details, events = events, score = score, openedLineup = details.storedLineup()) }
                subscribeToLiveUpdates()
            } catch (e: Exception) {
                _state.update { LiveMatchUiState.Error(getString(Res.string.msg_failed_to_load_match)) }
            }
        }
    }

    private fun subscribeToLiveUpdates() {
        wantsFeed = true
        if (feedJob?.isActive == true) return
        feedJob = viewModelScope.launch {
            observeLiveMatchUseCase(matchId).collect { event ->
                when (event) {
                    LiveFeedEvent.Connected -> {
                        setReconnecting(false)
                        if (missedUpdates) {
                            missedUpdates = false
                            reloadAfterReconnect()
                        }
                    }
                    LiveFeedEvent.Disconnected -> {
                        missedUpdates = true
                        setReconnecting(true)
                    }
                    // Not following live on the space's plan: whoever records still sees their own
                    // changes, which never wait on the socket.
                    LiveFeedEvent.NotInPlan -> setReconnecting(false)
                    is LiveFeedEvent.Update -> applyUpdate(event.value)
                }
            }
        }
    }

    private fun applyUpdate(update: LiveMatchUpdate) {
        // A lineup other than the one we last sent, while ours is pending or unechoed, is an
        // older echo: applying it would put the pitch back to a previous arrangement. The
        // socket delivers in order, so once ours comes back nothing older can follow.
        val lineupSlots = update.lineup?.slots
        val staleLineup = lineupSlots != null && (desiredLineup != null || (awaitingEcho != null && lineupSlots != awaitingEcho))
        if (lineupSlots != null && lineupSlots == awaitingEcho) awaitingEcho = null
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
                val formation = if (staleLineup) next.details.match.formation else match.formation
                next = next.copy(details = next.details.copy(match = match.copy(formation = formation)))
            }
            update.lineup?.takeIf { !staleLineup }?.let { lineup ->
                next = next.copy(details = next.details.withLineup(lineup))
            }
            next
        }
    }

    private fun setReconnecting(reconnecting: Boolean) =
        _state.update { (it as? LiveMatchUiState.Idle)?.copy(reconnecting = reconnecting) ?: it }

    /**
     * The match, events and score as the server has them, after the socket was down: what changed
     * meanwhile never reached this phone. Waits for this phone's own actions and lineup saves, so it
     * never undoes one, and takes the server's pitch (which has them). A failure keeps what's shown.
     */
    private fun reloadAfterReconnect() {
        viewModelScope.launch {
            submitMutex.withLock {
                lineupJob?.join()
                if (desiredLineup != null) return@withLock
                try {
                    val details = fetchDetails()
                    val events = getMatchEventsUseCase(matchId)
                    val score = getMatchScoreUseCase(matchId)
                    // Our last lineup's echo may have been missed too; the server's pitch has it.
                    awaitingEcho = null
                    _state.update { (it as? LiveMatchUiState.Idle)?.copy(details = details, events = events, score = score) ?: it }
                } catch (e: Exception) {
                    // Still connected: later updates keep coming, and the next reconnect reloads again.
                }
            }
        }
    }

    /** The app went to the background: the socket closes, and what's missed is reloaded on return. */
    fun onBackground() {
        feedJob?.cancel()
        feedJob = null
        if (wantsFeed) missedUpdates = true
    }

    /** Back in the foreground: the socket reopens (and, once open, reloads what was missed). */
    fun onForeground() {
        if (wantsFeed) subscribeToLiveUpdates()
    }

    override fun onTabSelected(tab: LiveMatchTab) {
        _state.update { if (it is LiveMatchUiState.Idle) it.copy(selectedTab = tab) else it }
    }

    fun clearError() {
        _state.update { if (it is LiveMatchUiState.Idle) it.copy(error = null) else it }
    }

    override fun onKickOff(halfLengthMinutes: Int) = runSubmitting { current ->
        val lineup = current.details.storedLineup()
        startMatchUseCase(matchId, StartMatchRequest(lineup.slots.values.toList(), halfLengthMinutes, lineup.formation))
        val details = fetchDetails()
        subscribeToLiveUpdates()
        ({ it.copy(details = details, events = emptyList(), score = MatchScore(matchId, 0, 0)) })
    }

    // Refetched rather than patched locally: the server records where the first half ended
    // (firstHalfEndMinute, including stoppage time), which the half-time minutes totals need.
    override fun onHalfTime() = runSubmitting { _ ->
        updateMatchStatusUseCase(matchId, MatchStatus.HALF_TIME)
        withFetchedDetails()
    }

    override fun onResumeSecondHalf() = runSubmitting { _ ->
        updateMatchStatusUseCase(matchId, MatchStatus.LIVE)
        // Mirrors what the server just persisted (UpdateMatchStatusUseCase sets this on the same
        // HALF_TIME -> LIVE transition) so the running clock picks it up immediately rather than
        // waiting on a refetch or the WebSocket echo.
        val startedAt = serverNow()
        ({
            it.copy(details = it.details.copy(match = it.details.match.copy(status = MatchStatus.LIVE, secondHalfStartedAt = startedAt, pausedAt = null)))
        })
    }

    // Refetched for the full-time minute and the match report card the summary links to.
    override fun onFullTime() = runSubmitting { _ ->
        updateMatchStatusUseCase(matchId, MatchStatus.FINISHED)
        withFetchedDetails()
    }

    /**
     * Shown at once and saved in the background: placing players before kickoff is many quick taps,
     * which shouldn't wait on the network. Saves go one at a time; taps made meanwhile are sent as one,
     * the latest, when the save in flight returns. A refused save reloads the server's pitch.
     */
    override fun onSetLineup(formation: String, slots: Map<Int, String>, teamSize: Int?) {
        if (_state.value !is LiveMatchUiState.Idle) return
        val lineup = MatchLineup(formation, slots)
        _state.update {
            (it as? LiveMatchUiState.Idle)?.let { idle ->
                val details = idle.details.withLineup(lineup)
                idle.copy(details = if (teamSize != null) details.withTeamSize(teamSize) else details, error = null)
            } ?: it
        }
        desiredLineup = lineup
        // Kept until sent: a later tap's lineup is already for the new count.
        if (teamSize != null) desiredTeamSize = teamSize
        if (lineupJob?.isActive != true) lineupJob = viewModelScope.launch { drainLineups() }
    }

    /**
     * "Save" before kickoff: every change is already sent as it's made, so this waits for the last one
     * to reach the server (runSubmitting runs after any pending lineup save) and then [onSaved] -- the
     * screen leaves. A refused save keeps the coach here with its error, on the server's pitch.
     * (`current` isn't used: the refusal is [lineupRefused], since its message may not load.)
     */
    override fun onSaveLineup(onSaved: () -> Unit) = runSubmitting { _ ->
        // A second tap, with nothing more pending, leaves on the server's pitch.
        val refused = lineupRefused
        lineupRefused = false
        if (!refused) onSaved()
        return@runSubmitting { it }
    }

    private suspend fun drainLineups() {
        while (true) {
            val next = desiredLineup ?: return
            desiredLineup = null
            val teamSize = desiredTeamSize
            desiredTeamSize = null
            awaitingEcho = next.slots
            try {
                setMatchLineupUseCase(matchId, SetMatchLineupRequest(next.formation, next.slots, teamSize))
                lineupRefused = false
            } catch (e: Exception) {
                lineupRefused = true
                desiredLineup = null
                desiredTeamSize = null
                awaitingEcho = null
                // The pitch first, so the coach never keeps working on one the server doesn't have.
                runCatching { fetchDetails() }.getOrNull()?.let { details ->
                    _state.update { (it as? LiveMatchUiState.Idle)?.copy(details = details) ?: it }
                }
                val message = runCatching { errorMessage(e, Res.string.msg_live_match_action_failed) }.getOrNull()
                _state.update { (it as? LiveMatchUiState.Idle)?.copy(error = message) ?: it }
                return
            }
        }
    }

    /** The match as the server has it now, applied onto the latest state. */
    private suspend fun withFetchedDetails(flash: LiveFlash? = null): (LiveMatchUiState.Idle) -> LiveMatchUiState.Idle {
        val details = fetchDetails()
        return { it.copy(details = details, flash = flash ?: it.flash) }
    }

    override fun clearFlash() {
        _state.update { if (it is LiveMatchUiState.Idle) it.copy(flash = null) else it }
    }

    /**
     * Freezes the running clock for a mid-half stoppage -- distinct from [onHalfTime], which is a
     * proper status transition. Refetches rather than computing the new state locally: the server is
     * the sole authority on match time (both here and for [onResumeClock] below), so there's no local
     * clock-skew risk from trusting this device's own `now()` for either the pause instant or the
     * resume-time shift math (see PauseMatchUseCase/ResumeMatchUseCase).
     */
    override fun onPauseClock() = runSubmitting { _ ->
        pauseMatchUseCase(matchId)
        withFetchedDetails()
    }

    override fun onResumeClock() = runSubmitting { _ ->
        resumeMatchUseCase(matchId)
        withFetchedDetails()
    }

    /**
     * A manual correction to the running clock -- server-persisted (unlike the local-only "nudge on
     * top of the derived value" this replaced, which never reached the server, the club Matches list,
     * or any other device watching the same match). Refetches for the same reason as [onPauseClock]/
     * [onResumeClock]: the server computes the actual shift, this device's own clock is never trusted.
     */
    override fun onSetMinute(minute: Int) = runSubmitting { _ ->
        setMatchMinuteUseCase(matchId, minute)
        withFetchedDetails(flash = LiveFlash.ClockSet(minute))
    }

    /** Our score box (unattributed) or the opponent's -- see LiveMatchScreen's score row. */
    override fun onLogGoal(minute: Int, isOpponent: Boolean) = addEvent(CreateMatchEventRequest(EventType.GOAL, minute, isOpponent = isOpponent))

    override fun onLogEvent(type: EventType, playerId: String, minute: Int) = addEvent(CreateMatchEventRequest(type, minute, playerId = playerId))

    /** One of our players scored; [assistPlayerId] is optional. The server saves the goal and its assist
     * together, so the event list is refetched to pick up the assist alongside the goal. */
    override fun onLogPlayerGoal(scorerId: String, assistPlayerId: String?, minute: Int) = runSubmitting { _ ->
        addMatchEventUseCase(matchId, CreateMatchEventRequest(EventType.GOAL, minute, playerId = scorerId, assistPlayerId = assistPlayerId))
        val events = getMatchEventsUseCase(matchId)
        val score = getMatchScoreUseCase(matchId)
        ({ it.copy(events = events.sortedBy { e -> e.minute }, score = score) })
    }

    override fun onSubstitute(outPlayerId: String, inPlayerId: String, minute: Int) = runSubmitting { _ ->
        val outEvent = addMatchEventUseCase(matchId, CreateMatchEventRequest(EventType.SUBSTITUTION_OUT, minute, playerId = outPlayerId))
        val inEvent = addMatchEventUseCase(matchId, CreateMatchEventRequest(EventType.SUBSTITUTION_IN, minute, playerId = inPlayerId))
        ({ it.copy(events = it.events.with(outEvent, inEvent), flash = LiveFlash.Substitution(minute, inPlayerId, outPlayerId)) })
    }

    override fun onDeleteEvent(eventId: String) = runSubmitting { _ ->
        deleteMatchEventUseCase(matchId, eventId)
        val score = getMatchScoreUseCase(matchId)
        // Deleting a goal also removes its linked assist on the server -- drop both here.
        ({ it.copy(events = it.events.filterNot { e -> e.id == eventId || e.relatedEventId == eventId }, score = score) })
    }

    /** Deletes both halves of a logged substitution together -- see LiveMatchScreen's paired row. */
    override fun onDeleteSubstitution(outEventId: String, inEventId: String) = runSubmitting { _ ->
        deleteMatchEventUseCase(matchId, outEventId)
        deleteMatchEventUseCase(matchId, inEventId)
        ({ it.copy(events = it.events.filterNot { e -> e.id == outEventId || e.id == inEventId }) })
    }

    private fun addEvent(request: CreateMatchEventRequest) = runSubmitting { _ ->
        val event = addMatchEventUseCase(matchId, request)
        val score = getMatchScoreUseCase(matchId)
        ({ it.copy(events = it.events.with(event), score = score) })
    }

    /** [added] in the event list once each (the socket may have brought them in already), by minute. */
    private fun List<MatchEvent>.with(vararg added: MatchEvent): List<MatchEvent> {
        val ids = added.map { it.id }.toSet()
        return (filterNot { it.id in ids } + added).sortedBy { it.minute }
    }

    /**
     * Runs one action. [block] gets the state as it is when the action's turn comes and does the network
     * calls; it returns a change to apply onto the state as it is when they finish -- not a snapshot,
     * which would wipe out a swap or socket update that landed in between. Actions run one at a time,
     * after any pending lineup save.
     */
    private fun runSubmitting(block: suspend (LiveMatchUiState.Idle) -> (LiveMatchUiState.Idle) -> LiveMatchUiState.Idle) {
        if (_state.value !is LiveMatchUiState.Idle) return
        submitsInFlight++
        _state.update { (it as? LiveMatchUiState.Idle)?.copy(isSubmitting = true, error = null) ?: it }
        viewModelScope.launch {
            submitMutex.withLock {
                try {
                    lineupJob?.join()
                    val current = _state.value as? LiveMatchUiState.Idle ?: return@withLock
                    // block() makes real network calls (POST/DELETE) -- it must run exactly once. Calling
                    // it directly inside _state.update {} is wrong: update() is a compare-and-swap retry
                    // loop, and any concurrent write to _state (e.g. subscribeToLiveUpdates' WebSocket
                    // handler firing for this same call's own broadcast) makes it retry the lambda, which
                    // would re-run block() and re-send the request -- this is what caused one tap to fire a
                    // burst of duplicate POSTs. Run it once, then apply its change (pure, safe to retry).
                    val change = block(current)
                    _state.update { (it as? LiveMatchUiState.Idle)?.let(change) ?: it }
                } catch (e: Exception) {
                    val message = errorMessage(e, Res.string.msg_live_match_action_failed)
                    _state.update { (it as? LiveMatchUiState.Idle)?.copy(error = message) ?: it }
                } finally {
                    submitsInFlight--
                    _state.update { (it as? LiveMatchUiState.Idle)?.copy(isSubmitting = submitsInFlight > 0) ?: it }
                }
            }
        }
    }
}
