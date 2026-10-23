package com.ricardofrango.squadify.presentation.ui.livematch

import com.ricardofrango.squadify.presentation.ui.components.homeFirst
import com.ricardofrango.squadify.model.ourSideFirst
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.PlayerPosition
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.presentation.ui.theme.OnDarkSurface
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchCallUpWithPlayer
import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.elapsedMatchSeconds
import com.ricardofrango.squadify.model.formationText
import com.ricardofrango.squadify.model.pitchSpot
import androidx.compose.runtime.saveable.rememberSaveable
import com.ricardofrango.squadify.presentation.ui.components.ClubLogo
import com.ricardofrango.squadify.presentation.ui.components.ConfirmDialog
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SuccessGreen
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import com.ricardofrango.squadify.presentation.ui.components.labelRes
import com.ricardofrango.squadify.presentation.ui.components.PlayerBadge
import squadify.app.shared.generated.resources.*
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Instant

private val CardShape = RoundedCornerShape(16.dp)
private val LiveRed = Color(0xFFC0392B)
private val CardYellow = Color(0xFFF1C40F)
// 45 is the standard 11-a-side half from the older age groups up.
private val HALF_LENGTH_OPTIONS = listOf(20, 25, 30, 40, 45)

/**
 * The touchline tool: the pitch before kickoff (placing the starters), the live pitch/squad/events
 * console, and the full-time summary, all in one dark screen (design canvas artboard "16" and the
 * "Squadify Pitch View" canvas, see AI_GUIDELINES.md "UI Design Reference"). Which layout renders is
 * driven entirely by `details.match.status`.
 *
 * Everything can be done by tapping; on the pitch, holding a player and dragging is a faster way to
 * place, substitute or swap them (PitchView.kt). The running clock and which half is "current" are derived from the match's persisted
 * kickoffAt/secondHalfStartedAt timestamps (see elapsedMatchSeconds below), so they're correct even
 * after leaving and reopening the screen mid-match. Everything that matters for the record (score,
 * events, status, kickoff/second-half timestamps) is server-authoritative.
 */
@Composable
fun LiveMatchScreen(
    matchId: String,
    onNavigateBack: () -> Unit,
    // The full-time summary's "Fill in the match report" (design 36e).
    onOpenReport: (matchId: String, edit: Boolean) -> Unit = { _, _ -> },
    viewModel: LiveMatchViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val uiState = viewModel.state.collectSettledState()

        LaunchedEffect(matchId) { viewModel.load(matchId) }

        // Insets are handled by hand below (ConsoleHeader's statusBarsPadding, the bottom action bars'
        // navigationBarsPadding) since the header/bottom bar are plain content, not Scaffold's topBar/
        // bottomBar slots. Scaffold's own default contentWindowInsets would apply the same system-bar
        // insets a second time on top of those -- pushing the header down and the bottom button up.
        // The console is dark whatever the club: a club's colour is lifted to read on it (ClubPalette).
        OnDarkSurface {
            Scaffold(containerColor = DeepCarbon, contentWindowInsets = WindowInsets(0)) { paddingValues ->
                Box(Modifier.fillMaxSize().padding(paddingValues).background(DeepCarbon)) {
                    when (val state = uiState) {
                        is LiveMatchUiState.Loading -> LoadingBody(onNavigateBack)
                        is LiveMatchUiState.Error -> ErrorBody(state.message, onNavigateBack)
                        is LiveMatchUiState.Idle -> IdleBody(state, viewModel, onNavigateBack, onOpenReport)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingBody(onNavigateBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ConsoleHeader(title = stringResource(Res.string.title_live_match), subtitle = null, onNavigateBack = onNavigateBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PitchGreen)
        }
    }
}

@Composable
private fun ErrorBody(message: String, onNavigateBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ConsoleHeader(title = stringResource(Res.string.title_live_match), subtitle = null, onNavigateBack = onNavigateBack)
        Box(Modifier.fillMaxSize().screenContentPadding(), contentAlignment = Alignment.Center) {
            Text(message, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun IdleBody(state: LiveMatchUiState.Idle, actions: LiveMatchActions, onNavigateBack: () -> Unit, onOpenReport: (String, Boolean) -> Unit) {
    when (state.details.match.status) {
        MatchStatus.SCHEDULED -> PreKickoffContent(state, actions, onNavigateBack)
        MatchStatus.LIVE, MatchStatus.HALF_TIME -> LiveConsoleContent(state, actions, onNavigateBack)
        MatchStatus.FINISHED, MatchStatus.CANCELLED -> SummaryContent(state, onNavigateBack, onOpenReport)
    }
}

// ---------------------------------------------------------------------------------------------
// Header shared by every phase
// ---------------------------------------------------------------------------------------------

@Composable
private fun ConsoleHeader(
    title: String,
    subtitle: String?,
    onNavigateBack: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                painter = painterResource(Res.drawable.ic_back),
                contentDescription = stringResource(Res.string.label_back),
                tint = Color.White
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(it, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }
        trailing()
    }
}

// ---------------------------------------------------------------------------------------------
// Pre-kickoff: placing the starters on the pitch (the live pitch view)
// ---------------------------------------------------------------------------------------------

private sealed class PreKickoffSheet {
    data class WhoPlaysHere(val slot: Int) : PreKickoffSheet()
    data class SlotOptions(val slot: Int, val player: Player) : PreKickoffSheet()
    data object Formation : PreKickoffSheet()
}

/**
 * Before kickoff the console opens straight on the pitch, every position empty. Tapping one picks a
 * called-up player for it; holding a player drags them to another position or back to the bench. Each
 * change is saved and shown on every device at once. Kick-off unlocks once every position is filled.
 */
@Composable
private fun PreKickoffContent(state: LiveMatchUiState.Idle, actions: LiveMatchActions, onNavigateBack: () -> Unit) {
    val details = state.details
    val canManage = details.canManageLiveMatch
    val teamSize = details.teamSize
    val formation = details.formation()
    val formationText = formation.formationText()
    val playersById = remember(details.callUps) { details.callUps.associate { it.callUp.playerId to it.player } }
    val slotIds = details.storedSlots()
    val slots = slotIds.mapNotNull { (slot, id) -> playersById[id]?.let { slot to it } }.toMap()
    val bench = details.callUps.map { it.player }.filter { it.id !in slotIds.values }
    val remaining = teamSize - slots.size

    var halfLength by rememberSaveable { mutableStateOf(30) }
    var sheet by remember { mutableStateOf<PreKickoffSheet?>(null) }
    var flipped by rememberSaveable { mutableStateOf(false) }
    val editable = canManage && !state.isSubmitting
    fun save(next: Map<Int, String>) = actions.onSetLineup(formationText, next)

    val onDrop: (PitchDropKey, PitchDropKey?) -> Unit = { from, to ->
        when {
            from is PitchDropKey.Bench && to is PitchDropKey.Slot -> save(slotIds.placed(to.slot, from.playerId))
            from is PitchDropKey.Slot && to is PitchDropKey.Slot -> save(slotIds.swapped(from.slot, to.slot))
            from is PitchDropKey.Slot && to is PitchDropKey.Bench -> save(slotIds.placed(from.slot, to.playerId))
            from is PitchDropKey.Slot && to == PitchDropKey.BenchArea -> save(slotIds - from.slot)
            else -> Unit
        }
    }

    val kickOffBar: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            if (canManage) {
                Button(
                    onClick = { actions.onKickOff(halfLength) },
                    enabled = remaining == 0 && !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PitchGreen,
                        contentColor = Color.White,
                        disabledContainerColor = CarbonGray,
                        disabledContentColor = Color.White.copy(alpha = 0.4f)
                    )
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text(stringResource(Res.string.btn_kick_off).uppercase(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (remaining > 0) {
                        stringResource(Res.string.lm_fill_more_to_kick_off, remaining, halfLength)
                    } else {
                        stringResource(Res.string.msg_ready_to_kick_off, halfLength)
                    },
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    stringResource(Res.string.msg_live_match_read_only),
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight && maxWidth >= 600.dp
        // No app bar: the pitch gets the room (system back leaves the screen).
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp)) {
            if (details.callUps.isEmpty()) {
                Text(
                    stringResource(Res.string.msg_no_callups),
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
            PitchPanel(
                formation = formation,
                slots = slots,
                bench = bench,
                sentOff = emptyList(),
                tokenInfo = { PitchTokenInfo() },
                horizontal = landscape,
                // The pitch faces the scoreboard: our goal on our side of it (home first, or ours on neutral ground).
                ourGoalAtStart = details.match.venue.ourSideFirst() != flipped,
                dragEnabled = editable,
                onSlotTap = if (editable) {
                    { slot, player -> sheet = if (player == null) PreKickoffSheet.WhoPlaysHere(slot) else PreKickoffSheet.SlotOptions(slot, player) }
                } else null,
                // A bench player tapped goes into the first empty position.
                onBenchTap = if (editable) {
                    { player -> slotIds.firstFreeSlot(teamSize)?.let { save(slotIds.placed(it, player.id)) } }
                } else null,
                onDrop = onDrop,
                header = {
                    FormationBar(formationText, onChange = if (editable) { { sheet = PreKickoffSheet.Formation } } else null, compact = landscape) {
                        HalfLengthChip(halfLength, HALF_LENGTH_OPTIONS, enabled = canManage) { halfLength = it }
                        if (landscape) FlipPitchButton { flipped = !flipped }
                    }
                },
                hint = null,
                modifier = Modifier.weight(1f).fillMaxWidth().then(if (landscape) Modifier.padding(horizontal = 16.dp, vertical = 8.dp) else Modifier),
                footer = if (landscape) { { kickOffBar(Modifier.fillMaxWidth().navigationBarsPadding()) } } else null
            )
            if (!landscape) {
                kickOffBar(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = ScreenPadding.Horizontal, vertical = 16.dp))
            }
        }
    }

    when (val s = sheet) {
        is PreKickoffSheet.WhoPlaysHere -> PitchPickSheet(
            title = stringResource(Res.string.lm_who_plays_here),
            subtitle = zoneLabel(pitchSpot(formation, s.slot).zone),
            sections = listOf(PitchPickSection(null, bench.map { p -> PitchPickRow(pickLabel(p), p) { save(slotIds.placed(s.slot, p.id)); sheet = null } })),
            emptyText = stringResource(Res.string.lm_everyone_placed),
            onDismiss = { sheet = null }
        )
        is PreKickoffSheet.SlotOptions -> PitchPickSheet(
            title = "${s.player.firstName} ${s.player.lastName}",
            subtitle = zoneLabel(pitchSpot(formation, s.slot).zone),
            sections = listOf(
                PitchPickSection(null, listOf(PitchPickRow(stringResource(Res.string.lm_take_off_pitch)) { save(slotIds - s.slot); sheet = null })),
                PitchPickSection(stringResource(Res.string.lm_put_in_instead), bench.map { p -> PitchPickRow(pickLabel(p), p) { save(slotIds.placed(s.slot, p.id)); sheet = null } }),
                PitchPickSection(
                    stringResource(Res.string.lm_swap_with),
                    slots.filterKeys { it != s.slot }.entries.sortedBy { it.key }.map { (slot, p) -> PitchPickRow(pickLabel(p), p) { save(slotIds.swapped(s.slot, slot)); sheet = null } }
                )
            ),
            emptyText = null,
            onDismiss = { sheet = null }
        )
        PreKickoffSheet.Formation -> FormationSheet(formationText, teamSize, onApply = { actions.onSetLineup(it, slotIds); sheet = null }, onDismiss = { sheet = null })
        null -> Unit
    }
}

// elapsedMatchSeconds(match, now) itself now lives in common/model/Models.kt -- shared with MatchRow's
// club Matches list badge so the two can never disagree (see that function's own doc comment).

/** The running match clock, e.g. 1502 seconds -> "25:02". */
private fun formatMatchClock(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

// ---------------------------------------------------------------------------------------------
// Live console (design 16A "is live")
// ---------------------------------------------------------------------------------------------

private sealed class ActionSheet {
    data class PlayerActions(val row: SquadPlayerRow, val onPitch: Boolean) : ActionSheet()
    // Step 2 of a substitution (design 36b): the candidates, with their minutes.
    data class PickComingOn(val outgoing: Player) : ActionSheet()
    data class PickGoingOff(val incoming: Player) : ActionSheet()
    // Step 2 of logging a goal: who assisted it (optional). [minute] is fixed when Goal was tapped.
    data class PickAssist(val scorerId: String, val scorerName: String, val minute: Int) : ActionSheet()
    // The pitch view: a player swapping places, or someone moving into an empty position.
    data class ChangePosition(val player: Player) : ActionSheet()
    data class MoveHere(val slot: Int) : ActionSheet()
}

/** The pitch as it stands, for the sheets that rearrange it. */
private class LivePitch(val formation: List<Int>, val slots: Map<Int, String>, val players: Map<String, Player>, val save: (Map<Int, String>) -> Unit)

/** "25+2'" / "25'": where the first half ended, against its nominal length. */
private fun firstHalfLabel(halfLength: Int, endMinute: Int): String =
    if (endMinute > halfLength) "$halfLength+${endMinute - halfLength}'" else "$endMinute'"

@Composable
private fun LiveConsoleContent(state: LiveMatchUiState.Idle, actions: LiveMatchActions, onNavigateBack: () -> Unit) {
    val details = state.details
    val canManage = details.canManageLiveMatch
    val status = details.match.status

    // The clock is derived from the match's persisted kickoffAt/secondHalfStartedAt timestamps (see
    // elapsedMatchSeconds), not a local counter -- so it survives leaving and returning to this screen
    // and is correct regardless of the device's timezone (Instant arithmetic is absolute). `tick`
    // exists only to force a recomposition every second; it isn't itself the clock.
    val isSecondHalf = details.match.secondHalfStartedAt != null
    var showClockSheet by remember { mutableStateOf(false) }
    var showHalfTimeConfirm by remember { mutableStateOf(false) }
    var showFullTimeConfirm by remember { mutableStateOf(false) }
    var actionSheet by remember { mutableStateOf<ActionSheet?>(null) }
    var deletingEvent by remember { mutableStateOf<DisplayEvent?>(null) }
    var tick by remember { mutableStateOf(0) }
    val isPaused = details.match.pausedAt != null

    // Keeps ticking (recomposing) throughout LIVE, paused or not -- elapsedMatchSeconds itself freezes
    // the value while match.pausedAt is set, so a wasted tick just recomputes the same frozen number
    // rather than needing its own separate on/off condition here.
    LaunchedEffect(status) {
        if (status == MatchStatus.LIVE) {
            while (true) {
                delay(1_000)
                tick++
            }
        }
    }
    // The confirmation line under the share (a substitution, a clock correction) fades after a while.
    LaunchedEffect(state.flash) {
        if (state.flash != null) {
            delay(5_000)
            actions.clearFlash()
        }
    }

    // A clock correction is server-persisted (onSetMinute) -- once applied, this recomputes straight
    // from the corrected details.match like any other change.
    // Counted on the server's clock (see LiveMatchViewModel.clockOffset), since every match timestamp is the server's.
    val clockOffset by actions.clockOffset.collectAsStateWithLifecycle()
    val displaySeconds = remember(tick, details.match, clockOffset) {
        elapsedMatchSeconds(details.match, Clock.System.now() + clockOffset)
    }
    val displayMinute = displaySeconds / 60
    val halfLength = details.match.halfLengthMinutes ?: 0
    val liveMinute = if (status == MatchStatus.LIVE) displayMinute else null

    // Minutes are recomputed every whole minute (and on any event or clock change), not every tick.
    val minutes = remember(details, state.events, liveMinute) { squadMinutes(details, state.events, liveMinute) }
    val squad = remember(details, state.events, minutes) { deriveSquad(details, state.events, minutes) }
    val displayEvents = remember(state.events) { buildDisplayEvents(state.events) }
    val playersById = remember(details) { details.callUps.associate { it.callUp.playerId to it.player } }

    // The pitch view: who stands where now, after the substitutions and red cards since it was arranged.
    val formation = details.formation()
    val formationText = formation.formationText()
    val onPitchIds = remember(squad) { squad.onPitch.map { it.player.id }.toSet() }
    val currentSlotIds = remember(details, state.events, onPitchIds) { details.currentSlots(state.events, onPitchIds) }
    val livePitch = LivePitch(formation, currentSlotIds, playersById) { actions.onSetLineup(formationText, it) }
    var flipped by rememberSaveable { mutableStateOf(false) }
    var showFormationSheet by remember { mutableStateOf(false) }

    val oneHalf = details.match.halves == 1
    val halfLabel = when {
        oneHalf -> stringResource(Res.string.lm_one_period)
        isSecondHalf -> stringResource(Res.string.label_second_half).uppercase()
        else -> stringResource(Res.string.label_first_half).uppercase()
    }
    // Stoppage time: how far the clock is past the end of the current half ("1ST HALF · +2").
    val stoppage = displayMinute - (if (isSecondHalf) halfLength * 2 else halfLength)
    val clockLabel = when {
        isPaused -> stringResource(Res.string.label_paused_badge).uppercase()
        halfLength > 0 && stoppage > 0 -> "$halfLabel · +$stoppage"
        else -> halfLabel
    }
    val firstHalfEnd = details.match.firstHalfEndMinute ?: halfLength
    val flashText = when (val f = state.flash) {
        is LiveFlash.Substitution -> stringResource(
            Res.string.lm_sub_done,
            mins(f.minute),
            playersById[f.inPlayerId]?.let { "${it.firstName} ${it.lastName}" } ?: "-",
            playersById[f.outPlayerId]?.let { "${it.firstName} ${it.lastName}" } ?: "-"
        )
        is LiveFlash.ClockSet -> stringResource(Res.string.lm_clock_set, f.minute)
        null -> null
    }

    val scoreEnabled = canManage && status == MatchStatus.LIVE && !isPaused && !state.isSubmitting
    // The scoreboard (design "Squadify Pitch View"): home first, as football reads it, our club first
    // on neutral ground. Each score box logs a goal for its own side.
    val ourFirst = details.match.venue.ourSideFirst()
    val ours = ScoreSide(details.team.name, details.clubLogoUrl, state.score.ourScore, if (scoreEnabled) { { actions.onLogGoal(displayMinute, isOpponent = false) } } else null)
    val theirs = ScoreSide(details.opponent.shortName ?: details.opponent.name, details.opponent.logoUrl, state.score.opponentScore, if (scoreEnabled) { { actions.onLogGoal(displayMinute, isOpponent = true) } } else null)
    val home = if (ourFirst) ours else theirs
    val away = if (ourFirst) theirs else ours
    val clock = ClockState(
        clock = formatMatchClock(displaySeconds),
        label = clockLabel,
        paused = isPaused,
        // At half time there's nothing to pause or correct (36c).
        halfTimeNote = if (status == MatchStatus.HALF_TIME) stringResource(Res.string.lm_half_time_sub, firstHalfLabel(halfLength, firstHalfEnd)) else null,
        onClockClick = if (canManage && !state.isSubmitting) { { showClockSheet = true } } else null,
        onPauseToggle = if (canManage && status == MatchStatus.LIVE) { { if (!state.isSubmitting) { if (isPaused) actions.onResumeClock() else actions.onPauseClock() } } } else null
    )
    val tabs: @Composable (Modifier) -> Unit = { modifier ->
        ConsoleTabs(
            listOf(
                stringResource(Res.string.tab_pitch) to null,
                stringResource(Res.string.tab_squad) to null,
                stringResource(Res.string.lm_tab_events) to state.events.size.toString()
            ),
            selected = state.selectedTab.ordinal,
            onSelect = { actions.onTabSelected(LiveMatchTab.entries[it]) },
            modifier = modifier
        )
    }
    val tabContent: @Composable (sideBySide: Boolean) -> Unit = { sideBySide ->
        when (state.selectedTab) {
            LiveMatchTab.PITCH -> {
                val rowsById = (squad.onPitch + squad.bench + squad.sentOff).associateBy { it.player.id }
                val editable = canManage && !state.isSubmitting
                // Dragging: a bench player onto someone on the pitch (or the other way round) is a
                // substitution, the player coming on taking that position; two on the pitch swap places;
                // someone on the pitch dropped on the bench asks who comes on.
                val onDrop: (PitchDropKey, PitchDropKey?) -> Unit = { from, to ->
                    when {
                        from is PitchDropKey.Bench && to is PitchDropKey.Slot ->
                            currentSlotIds[to.slot]?.let { out -> actions.onSubstitute(out, from.playerId, displayMinute) }
                        from is PitchDropKey.Slot && to is PitchDropKey.Slot ->
                            actions.onSetLineup(formationText, currentSlotIds.swapped(from.slot, to.slot))
                        from is PitchDropKey.Slot && to is PitchDropKey.Bench ->
                            currentSlotIds[from.slot]?.let { out -> actions.onSubstitute(out, to.playerId, displayMinute) }
                        from is PitchDropKey.Slot && to == PitchDropKey.BenchArea ->
                            currentSlotIds[from.slot]?.let { playersById[it] }?.let { actionSheet = ActionSheet.PickComingOn(it) }
                        else -> Unit
                    }
                }
                PitchPanel(
                    formation = formation,
                    slots = currentSlotIds.mapNotNull { (slot, id) -> playersById[id]?.let { slot to it } }.toMap(),
                    bench = squad.bench.map { it.player },
                    sentOff = squad.sentOff.map { it.player },
                    tokenInfo = { p -> PitchTokenInfo(minutesFraction = if (minutes.showShare) minutes.barFraction(p.id) else null, booked = rowsById[p.id]?.hasYellow == true) },
                    horizontal = sideBySide,
                    ourGoalAtStart = details.match.venue.ourSideFirst() != flipped,
                    dragEnabled = editable,
                    onSlotTap = if (canManage) {
                        { slot, player ->
                            actionSheet = when {
                                player != null -> rowsById[player.id]?.let { ActionSheet.PlayerActions(it, onPitch = true) }
                                // An empty position (after a red card): someone on the pitch can move into it.
                                else -> ActionSheet.MoveHere(slot)
                            }
                        }
                    } else null,
                    // A bench player can only come on, so tapping one goes straight to "who goes off?".
                    onBenchTap = if (canManage) { { player -> actionSheet = ActionSheet.PickGoingOff(player) } } else null,
                    onDrop = onDrop,
                    header = {
                        FormationBar(formationText, onChange = if (editable) { { showFormationSheet = true } } else null, compact = sideBySide) {
                            if (sideBySide) FlipPitchButton { flipped = !flipped }
                        }
                    },
                    // A substitution or clock correction just made is confirmed here, as on the Squad tab.
                    hint = flashText,
                    modifier = Modifier.fillMaxSize().padding(top = 4.dp).then(if (sideBySide) Modifier.padding(horizontal = 16.dp, vertical = 8.dp) else Modifier)
                )
            }
            LiveMatchTab.SQUAD -> SquadMinutesTab(
                squad = squad,
                minutes = minutes,
                isPaused = isPaused,
                isHalfTime = status == MatchStatus.HALF_TIME,
                firstHalfLabel = firstHalfLabel(halfLength, firstHalfEnd),
                flash = flashText,
                canManage = canManage,
                sideBySide = sideBySide
            ) { row, onPitch ->
                // A bench player can only come on, so tapping one goes straight to "who goes off?".
                actionSheet = if (onPitch) ActionSheet.PlayerActions(row, true) else ActionSheet.PickGoingOff(row.player)
            }
            LiveMatchTab.EVENTS -> EventsTab(displayEvents, details, onDeleteRequest = if (canManage) { { deletingEvent = it } } else null)
        }
    }
    val actionBar: @Composable (Modifier, Boolean) -> Unit = { modifier, compact ->
        val buttonHeight = if (compact) 40.dp else 52.dp
        Column(modifier) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            if (canManage) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        // Half time: the only way on is the second half.
                        status == MatchStatus.HALF_TIME -> Button(
                            onClick = { actions.onResumeSecondHalf() },
                            enabled = !state.isSubmitting,
                            modifier = Modifier.weight(1f).height(buttonHeight),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White)
                        ) { Text(stringResource(Res.string.btn_start_second_half), fontWeight = FontWeight.Bold) }
                        else -> {
                            // There's no second interval: once the second half has started, only Full time.
                            // One-half matches have no interval at all.
                            if (!isSecondHalf && !oneHalf) {
                                OutlinedButton(
                                    onClick = { showHalfTimeConfirm = true },
                                    enabled = !state.isSubmitting,
                                    modifier = Modifier.weight(1f).height(buttonHeight),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) { Text(stringResource(Res.string.btn_half_time), fontWeight = FontWeight.Bold) }
                            }
                            OutlinedButton(
                                onClick = { showFullTimeConfirm = true },
                                enabled = !state.isSubmitting,
                                modifier = Modifier.weight(1f).height(buttonHeight),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, LiveRed),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveRed)
                            ) { Text(stringResource(Res.string.btn_full_time), fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight && maxWidth >= 600.dp
        if (landscape) {
            // Lying down (design "Squadify Pitch View"): one scoreboard bar across the top, then the
            // tabs with the match buttons beside them, and the tab's content (the pitch with the bench
            // in a column beside it) below.
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(painterResource(Res.drawable.ic_back), contentDescription = stringResource(Res.string.label_back), tint = Color.White)
                    }
                    ScoreboardBar(home, away, clock, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    tabs(Modifier.widthIn(max = 380.dp).weight(1f))
                    Spacer(Modifier.width(16.dp))
                    actionBar(Modifier.width(300.dp), true)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { tabContent(true) }
            }
        } else {
            // No app bar: the scoreboard sits at the top (system back leaves the screen).
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp)) {
                ScoreboardCard(home, away, clock, Modifier.padding(horizontal = ScreenPadding.Horizontal))
                tabs(Modifier.padding(horizontal = ScreenPadding.Horizontal).padding(top = 12.dp))
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) { tabContent(false) }
                actionBar(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = ScreenPadding.Horizontal, vertical = 16.dp), false)
            }
        }
    }

    if (showClockSheet) {
        ClockCorrectionSheet(
            initialMinute = displayMinute,
            isPaused = isPaused,
            onSave = {
                actions.onSetMinute(it)
                showClockSheet = false
            },
            onDismiss = { showClockSheet = false }
        )
    }
    if (showHalfTimeConfirm) {
        ConfirmDialog(
            title = stringResource(Res.string.msg_confirm_half_time_title),
            body = stringResource(Res.string.msg_confirm_half_time_body),
            onConfirm = { showHalfTimeConfirm = false; actions.onHalfTime() },
            onDismiss = { showHalfTimeConfirm = false }
        )
    }
    if (showFullTimeConfirm) {
        ConfirmDialog(
            title = stringResource(Res.string.msg_confirm_full_time_title),
            body = stringResource(Res.string.msg_confirm_full_time_body),
            onConfirm = { showFullTimeConfirm = false; actions.onFullTime() },
            onDismiss = { showFullTimeConfirm = false }
        )
    }
    deletingEvent?.let { de ->
        ConfirmDialog(
            title = stringResource(Res.string.msg_confirm_delete_event_title),
            body = stringResource(Res.string.msg_confirm_delete_event_body),
            onConfirm = {
                when (de) {
                    is DisplayEvent.Simple -> actions.onDeleteEvent(de.event.id)
                    is DisplayEvent.Substitution -> actions.onDeleteSubstitution(de.out.id, de.into.id)
                }
                deletingEvent = null
            },
            onDismiss = { deletingEvent = null }
        )
    }
    if (showFormationSheet) {
        // The players keep their position numbers: who stands where in the new shape is the coach's call.
        FormationSheet(formationText, details.teamSize, onApply = { actions.onSetLineup(it, currentSlotIds); showFormationSheet = false }, onDismiss = { showFormationSheet = false })
    }
    actionSheet?.let { sheet ->
        PlayerActionSheet(
            sheet = sheet,
            pitch = livePitch,
            squad = squad,
            minutes = minutes,
            minute = displayMinute,
            actions = actions,
            enabled = !state.isSubmitting,
            onDismiss = { actionSheet = null },
            onChangeSheet = { actionSheet = it }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Tap-a-player action sheet
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerActionSheet(
    sheet: ActionSheet,
    pitch: LivePitch,
    squad: DerivedSquad,
    minutes: SquadMinutes,
    minute: Int,
    actions: LiveMatchActions,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onChangeSheet: (ActionSheet?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = CarbonGray) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding.Horizontal, vertical = 8.dp).navigationBarsPadding()) {
            when (sheet) {
                is ActionSheet.PlayerActions -> {
                    val player = sheet.row.player
                    val name = "${player.firstName} ${player.lastName}"
                    // Design "Squadify Pitch View": photo, name and "#10 · Midfield · 37' on the pitch", OK to close.
                    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        TokenPhoto(player, 44.dp, BorderStroke(2.dp, Color.White))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val detail = listOfNotNull(
                                player.shirtNumber?.let { "#$it" },
                                player.position?.let { stringResource(it.labelRes()) },
                                if (sheet.onPitch) stringResource(Res.string.lm_on_the_pitch_for, mins(minutes.minutes(player.id))) else null
                            ).joinToString(" · ")
                            if (detail.isNotEmpty()) Text(detail, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.btn_ok), color = PitchGreen) }
                    }
                    if (sheet.onPitch) {
                        ActionTileRow {
                            // A goal goes on to an optional "who assisted?" step; assists are only logged
                            // that way, attached to their goal.
                            ActionTile(stringResource(Res.string.lm_action_goal), ActionTone.GOAL, enabled, Modifier.weight(1f)) {
                                onChangeSheet(ActionSheet.PickAssist(player.id, name, minute))
                            }
                            ActionTile(stringResource(Res.string.action_log_own_goal), ActionTone.PLAIN, enabled, Modifier.weight(1f)) {
                                actions.onLogEvent(EventType.OWN_GOAL, player.id, minute); onDismiss()
                            }
                        }
                        ActionTileRow {
                            ActionTile(stringResource(Res.string.lm_action_yellow), ActionTone.YELLOW, enabled, Modifier.weight(1f)) {
                                actions.onLogEvent(EventType.YELLOW_CARD, player.id, minute); onDismiss()
                            }
                            ActionTile(stringResource(Res.string.lm_action_red), ActionTone.RED, enabled, Modifier.weight(1f)) {
                                actions.onLogEvent(EventType.RED_CARD, player.id, minute); onDismiss()
                            }
                        }
                        if (squad.bench.isNotEmpty()) {
                            ActionTileRow {
                                ActionTile(stringResource(Res.string.lm_action_substitution), ActionTone.PLAIN, enabled, Modifier.weight(1f)) {
                                    onChangeSheet(ActionSheet.PickComingOn(player))
                                }
                            }
                        }
                        if (squad.onPitch.size > 1 || pitch.slots.size < pitch.formation.sum() + 1) {
                            ActionTileRow {
                                ActionTile(stringResource(Res.string.lm_change_position), ActionTone.PLAIN, enabled, Modifier.weight(1f)) {
                                    onChangeSheet(ActionSheet.ChangePosition(player))
                                }
                            }
                        }
                    } else if (squad.onPitch.isNotEmpty()) {
                        ActionTileRow {
                            ActionTile(stringResource(Res.string.action_substitute_on), ActionTone.PLAIN, enabled, Modifier.weight(1f)) {
                                onChangeSheet(ActionSheet.PickGoingOff(player))
                            }
                        }
                    }
                }

                is ActionSheet.PickComingOn -> SubstitutionPicker(sheet.outgoing, takingOff = true, candidates = squad.bench, minutes = minutes, enabled = enabled) { incoming ->
                    actions.onSubstitute(sheet.outgoing.id, incoming.id, minute); onDismiss()
                }

                is ActionSheet.PickAssist -> {
                    Text(
                        stringResource(Res.string.title_pick_assist, sheet.scorerName),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    // The assist is optional: "No assist" first, then the scorer's teammates on the pitch.
                    SheetAction(stringResource(Res.string.action_no_assist), enabled = enabled) {
                        actions.onLogPlayerGoal(sheet.scorerId, assistPlayerId = null, minute = sheet.minute); onDismiss()
                    }
                    squad.onPitch.filter { it.player.id != sheet.scorerId }.forEach { row ->
                        SheetAction(pickerLabel(row.player), enabled = enabled) {
                            actions.onLogPlayerGoal(sheet.scorerId, assistPlayerId = row.player.id, minute = sheet.minute); onDismiss()
                        }
                    }
                }

                is ActionSheet.PickGoingOff -> SubstitutionPicker(sheet.incoming, takingOff = false, candidates = squad.onPitch, minutes = minutes, enabled = enabled) { outgoing ->
                    actions.onSubstitute(outgoing.id, sheet.incoming.id, minute); onDismiss()
                }

                // Swap places with someone else on the pitch, or move into an empty position.
                is ActionSheet.ChangePosition -> {
                    val mine = pitch.slots.entries.firstOrNull { it.value == sheet.player.id }?.key
                    Text(
                        stringResource(Res.string.lm_swap_with),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (mine != null) {
                        (0..pitch.formation.sum()).forEach { slot ->
                            if (slot == mine) return@forEach
                            val other = pitch.slots[slot]?.let { pitch.players[it] }
                            val label = other?.let(::pickLabel)
                                ?: stringResource(Res.string.lm_empty_in_zone, zoneLabel(pitchSpot(pitch.formation, slot).zone))
                            SheetAction(label, enabled = enabled) { pitch.save(pitch.slots.swapped(mine, slot)); onDismiss() }
                        }
                    }
                }

                is ActionSheet.MoveHere -> {
                    Text(
                        stringResource(Res.string.lm_who_moves_here),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(zoneLabel(pitchSpot(pitch.formation, sheet.slot).zone), color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                    pitch.slots.entries.sortedBy { it.key }.forEach { (slot, id) ->
                        val player = pitch.players[id] ?: return@forEach
                        SheetAction(pickLabel(player), enabled = enabled) { pitch.save(pitch.slots.swapped(slot, sheet.slot)); onDismiss() }
                    }
                }
            }
        }
    }
}

/** "- 10 · Tomás Bragança", or "- Tomás Bragança" when the player has no shirt number. */
private fun pickerLabel(player: Player): String {
    val name = "${player.firstName} ${player.lastName}"
    return player.shirtNumber?.let { "- $it · $name" } ?: "- $name"
}

@Composable
private fun SheetAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), color = Color.Transparent) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 14.dp),
            color = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Events tab + shared event row (also used by the full-time summary)
// ---------------------------------------------------------------------------------------------

private sealed class DisplayEvent {
    abstract val minute: Int
    abstract val key: String
    abstract val createdAt: Instant

    // [assist]: for one of our goals, the ASSIST linked to it (MatchEvent.relatedEventId), shown on the same line.
    data class Simple(val event: MatchEvent, val assist: MatchEvent? = null) : DisplayEvent() {
        override val minute get() = event.minute
        override val key get() = event.id
        override val createdAt get() = event.createdAt
    }

    data class Substitution(val out: MatchEvent, val into: MatchEvent) : DisplayEvent() {
        override val minute get() = out.minute
        override val key get() = "${out.id}-${into.id}"
        override val createdAt get() = out.createdAt
    }
}

/** Pairs SUBSTITUTION_OUT/IN rows logged together (see LiveMatchViewModel.onSubstitute) into one
 * display row, newest first -- matching the full-time summary's timeline order. */
private fun buildDisplayEvents(events: List<MatchEvent>): List<DisplayEvent> {
    val outs = events.filter { it.type == EventType.SUBSTITUTION_OUT }
    val remainingIns = events.filter { it.type == EventType.SUBSTITUTION_IN }.toMutableList()
    val paired = mutableListOf<DisplayEvent.Substitution>()
    for (out in outs) {
        val match = remainingIns.firstOrNull { it.minute == out.minute }
        if (match != null) {
            remainingIns.remove(match)
            paired += DisplayEvent.Substitution(out, match)
        }
    }
    val pairedIds = paired.flatMap { listOf(it.out.id, it.into.id) }.toSet()
    // A linked assist is shown on its goal's line, not as a row of its own.
    val eventIds = events.map { it.id }.toSet()
    val assistByGoal = events
        .filter { it.type == EventType.ASSIST && it.relatedEventId != null && it.relatedEventId in eventIds }
        .associateBy { it.relatedEventId!! }
    val linkedAssistIds = assistByGoal.values.map { it.id }.toSet()
    val simples = events
        .filter { it.id !in pairedIds && it.id !in linkedAssistIds }
        .map { DisplayEvent.Simple(it, assistByGoal[it.id]) }
    return (paired + simples).sortedWith(compareByDescending<DisplayEvent> { it.minute }.thenByDescending { it.createdAt })
}

@Composable
private fun EventsTab(displayEvents: List<DisplayEvent>, details: MatchDetails, onDeleteRequest: ((DisplayEvent) -> Unit)?) {
    if (displayEvents.isEmpty()) {
        Box(Modifier.fillMaxSize().screenContentPadding(), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.msg_no_events_yet), color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ScreenPadding.Horizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(displayEvents, key = { it.key }) { de ->
            EventRow(de, details, onDelete = onDeleteRequest?.let { { it(de) } })
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun EventRow(de: DisplayEvent, details: MatchDetails, onDelete: (() -> Unit)?) {
    val playersById = remember(details) { details.callUps.associate { it.callUp.playerId to it.player } }
    fun nameFor(event: MatchEvent) = event.playerId?.let { playersById[it] }?.let { "${it.firstName} ${it.lastName}" } ?: "-"

    val (glyph, glyphColor, text) = when (de) {
        is DisplayEvent.Simple -> eventLineFor(de.event, details, ::nameFor, assistName = de.assist?.let(::nameFor))
        is DisplayEvent.Substitution -> Triple(
            "⇄",
            Color.White,
            stringResource(Res.string.label_event_substitution, nameFor(de.out), nameFor(de.into))
        )
    }

    Surface(modifier = Modifier.fillMaxWidth(), shape = CardShape, color = CarbonGray) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${de.minute}'",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.width(36.dp)
            )
            Box(
                Modifier.size(26.dp).background(glyphColor.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(glyph, color = glyphColor, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(10.dp))
            Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (onDelete != null) {
                Text(
                    "✕",
                    color = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDelete
                    ).padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun eventLineFor(event: MatchEvent, details: MatchDetails, nameFor: (MatchEvent) -> String, assistName: String? = null): Triple<String, Color, String> {
    val ourName = event.playerId?.let { nameFor(event) }
    val opponentName = details.opponent.shortName ?: details.opponent.name
    return when (event.type) {
        EventType.GOAL -> when {
            event.isOpponent -> Triple("•", Color.White.copy(alpha = 0.6f), stringResource(Res.string.label_event_goal, opponentName))
            ourName != null && assistName != null -> Triple("G", PitchGreen, stringResource(Res.string.label_event_goal_with_assist, ourName, assistName))
            ourName != null -> Triple("G", PitchGreen, stringResource(Res.string.label_event_goal, ourName))
            else -> Triple("G", PitchGreen, stringResource(Res.string.label_event_goal_unattributed))
        }
        EventType.OWN_GOAL -> Triple("OG", Color.White.copy(alpha = 0.6f), stringResource(Res.string.label_event_own_goal, ourName ?: "-"))
        EventType.ASSIST -> Triple("A", PitchGreen, stringResource(Res.string.label_event_assist, ourName ?: "-"))
        EventType.YELLOW_CARD -> Triple("Y", CardYellow, stringResource(Res.string.label_event_yellow_card, ourName ?: "-"))
        EventType.RED_CARD -> Triple("R", LiveRed, stringResource(Res.string.label_event_red_card, ourName ?: "-"))
        EventType.SUBSTITUTION_IN, EventType.SUBSTITUTION_OUT -> Triple("⇄", Color.White, ourName ?: "-")
    }
}

// ---------------------------------------------------------------------------------------------
// Full-time / cancelled summary
// ---------------------------------------------------------------------------------------------

@Composable
private fun SummaryContent(state: LiveMatchUiState.Idle, onNavigateBack: () -> Unit, onOpenReport: (String, Boolean) -> Unit) {
    val details = state.details
    val minutes = remember(details, state.events) { squadMinutes(details, state.events, liveMinute = null) }
    val outcome: Pair<org.jetbrains.compose.resources.StringResource, Color> = when {
        details.match.status == MatchStatus.CANCELLED -> Res.string.label_result_cancelled to Color.White.copy(alpha = 0.5f)
        state.score.ourScore > state.score.opponentScore -> Res.string.label_result_win to SuccessGreen
        state.score.ourScore < state.score.opponentScore -> Res.string.label_result_loss to LiveRed
        else -> Res.string.label_result_draw to Color.White.copy(alpha = 0.6f)
    }

    Column(Modifier.fillMaxSize()) {
        ConsoleHeader(
            title = "${details.ageGroup.name} · ${details.season.name}",
            subtitle = null,
            onNavigateBack = onNavigateBack,
            trailing = { ResultBadge(outcome.first, outcome.second) }
        )

        Column(
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding.Horizontal, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Home on top and its score first; our club at home or on neutral ground.
            val ourFirst = details.match.venue.ourSideFirst()
            Text(
                if (ourFirst) details.team.name else details.opponent.name,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                state.score.homeFirst(details.match.venue, separator = "-"),
                color = Color.White,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black)
            )
            if (details.match.status != MatchStatus.CANCELLED) {
                Text(
                    stringResource(Res.string.label_full_time),
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(if (ourFirst) details.opponent.name else details.team.name, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
        }

        // Design 36e: the goals in one line, then everyone's minutes -- the spread at a glance before
        // leaving the pitch -- and straight into the match report, which carries the same minutes.
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = ScreenPadding.Horizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { CompactEvents(details, state.events) }
            if (details.match.status == MatchStatus.FINISHED && details.callUps.isNotEmpty()) {
                item { MinutesTable(details, minutes) }
            }
            details.report?.let { report ->
                item {
                    Button(
                        onClick = { onOpenReport(details.match.id, report.canEdit) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White)
                    ) {
                        Text(stringResource(if (report.canEdit) Res.string.lm_fill_report else Res.string.lm_view_report), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = ScreenPadding.Horizontal, vertical = 16.dp).height(52.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text(stringResource(Res.string.btn_back_to_match), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultBadge(label: org.jetbrains.compose.resources.StringResource, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.15f), border = BorderStroke(1.dp, color)) {
        Text(
            stringResource(label),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
        )
    }
}

/** A small logo before a side's name on the scoreboard, on a white disc so any crest reads on the dark console. */
@Composable
private fun NameCrest(logoUrl: String, name: String) {
    ClubLogo(logoUrl, name.take(2).uppercase(), size = 20.dp, background = Color.White, initialsColor = DeepCarbon)
    Spacer(Modifier.width(6.dp))
}

/** Does nothing: the previews' stand-in for [LiveMatchViewModel]. */
private object PreviewLiveMatchActions : LiveMatchActions {
    override val clockOffset: StateFlow<Duration> = MutableStateFlow(Duration.ZERO)
    override fun clearFlash() {}
    override fun onDeleteEvent(eventId: String) {}
    override fun onDeleteSubstitution(outEventId: String, inEventId: String) {}
    override fun onFullTime() {}
    override fun onHalfTime() {}
    override fun onKickOff(halfLengthMinutes: Int) {}
    override fun onSetLineup(formation: String, slots: Map<Int, String>) {}
    override fun onLogEvent(type: EventType, playerId: String, minute: Int) {}
    override fun onLogGoal(minute: Int, isOpponent: Boolean) {}
    override fun onLogPlayerGoal(scorerId: String, assistPlayerId: String?, minute: Int) {}
    override fun onPauseClock() {}
    override fun onResumeClock() {}
    override fun onResumeSecondHalf() {}
    override fun onSetMinute(minute: Int) {}
    override fun onSubstitute(outPlayerId: String, inPlayerId: String, minute: Int) {}
    override fun onTabSelected(tab: LiveMatchTab) {}
}

private fun previewLiveDetails(match: Match, placed: Int = 7): MatchDetails {
    fun player(id: String, first: String, last: String, number: Int?, position: PlayerPosition) =
        Player(id, "c1", first, last, LocalDate(2013, 3, 12), shirtNumber = number, active = true, position = position)
    val squad = listOf(
        player("p1", "Ana", "Costa", 1, PlayerPosition.GOALKEEPER),
        player("p2", "Rui", "Perestrelo", 4, PlayerPosition.DEFENDER),
        player("p3", "Tiago", "Mendes", 3, PlayerPosition.DEFENDER),
        player("p4", "Tomás", "Bragança", 10, PlayerPosition.MIDFIELD),
        player("p5", "Miguel", "Sousa", 8, PlayerPosition.MIDFIELD),
        player("p6", "Nuno", "Dias", null, PlayerPosition.WINGER),
        player("p7", "Beatriz", "Almeida", 9, PlayerPosition.STRIKER),
        player("p8", "Rafael", "Lopes", 12, PlayerPosition.MIDFIELD),
        player("p9", "Gonçalo", "Pires", 14, PlayerPosition.STRIKER)
    )
    return MatchDetails(
        match = match,
        opponent = Opponent(id = "o1", clubId = "c1", name = "AD Fontelas", shortName = "Fontelas"),
        team = Team(id = "t1", clubId = "c1", ageGroupId = "ag1", seasonId = "s1", name = "Sub-13 A", active = true, teamSize = 7),
        ageGroup = AgeGroup(id = "ag1", clubId = "c1", name = "Sub-13", seasonId = "s1", startYear = 2013, endYear = 2013),
        season = Season("s1", "c1", "26/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 7, 1), LocalDate(2027, 6, 30), true),
        callUps = squad.mapIndexed { i, p ->
            MatchCallUpWithPlayer(
                MatchCallUp(id = "cu$i", matchId = match.id, playerId = p.id, status = CallUpStatus.CALLED_UP, isStarting = i < 7, pitchSlot = i.takeIf { it < placed }),
                p
            )
        },
        poolPlayers = emptyList(),
        canManageMatch = true,
        canManageCallUps = true,
        canManageLiveMatch = true,
        score = MatchScore(match.id, 1, 0)
    )
}

@Preview
@Composable
private fun LiveMatchPreKickoffPreview() {
    val match = Match(id = "m1", clubId = "c1", teamId = "t1", opponentId = "o1", dateTime = Instant.fromEpochMilliseconds(0), status = MatchStatus.SCHEDULED)
    SquadifyTheme {
        IdleBody(LiveMatchUiState.Idle(details = previewLiveDetails(match, placed = 4), score = MatchScore("m1", 0, 0)), PreviewLiveMatchActions, {}, { _, _ -> })
    }
}

/** Every position filled: Kick-off is ready. */
@Preview
@Composable
private fun LiveMatchPreKickoffReadyPreview() {
    val match = Match(id = "m1", clubId = "c1", teamId = "t1", opponentId = "o1", dateTime = Instant.fromEpochMilliseconds(0), status = MatchStatus.SCHEDULED)
    SquadifyTheme {
        IdleBody(LiveMatchUiState.Idle(details = previewLiveDetails(match), score = MatchScore("m1", 0, 0)), PreviewLiveMatchActions, {}, { _, _ -> })
    }
}

/** The Pitch tab during the game: a booking and a red card that left a position empty. */
@Preview
@Composable
private fun LiveMatchConsolePreview() {
    val match = Match(
        id = "m1", clubId = "c1", teamId = "t1", opponentId = "o1", dateTime = Instant.fromEpochMilliseconds(0),
        status = MatchStatus.LIVE, halfLengthMinutes = 30, kickoffAt = Instant.fromEpochMilliseconds(0)
    )
    SquadifyTheme {
        val events = listOf(
            MatchEvent("e1", "m1", EventType.YELLOW_CARD, 12, playerId = "p3", createdAt = Instant.fromEpochMilliseconds(1), period = 1),
            MatchEvent("e2", "m1", EventType.RED_CARD, 20, playerId = "p6", createdAt = Instant.fromEpochMilliseconds(2), period = 1)
        )
        IdleBody(LiveMatchUiState.Idle(details = previewLiveDetails(match), events = events, score = MatchScore("m1", 1, 0)), PreviewLiveMatchActions, {}, { _, _ -> })
    }
}

