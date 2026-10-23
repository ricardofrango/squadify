package com.ricardofrango.squadify.presentation.ui.livematch

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
import com.ricardofrango.squadify.presentation.ui.components.ClubLogo
import com.ricardofrango.squadify.presentation.ui.components.ConfirmDialog
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SuccessGreen
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
 * The touchline tool: pre-kickoff line-up picker, the live squad/events console, and the full-time
 * summary, all in one dark screen (design canvas artboard "16", see AI_GUIDELINES.md "UI Design
 * Reference"). Which layout renders is driven entirely by `details.match.status`.
 *
 * One disclosed simplification versus the mock: substitutions are tap-to-pick rather than
 * drag-to-swap (the hint text and the action sheets reflect this -- "tap a player to log" is still
 * accurate). The running clock and which half is "current" are derived from the match's persisted
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
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(message, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun IdleBody(state: LiveMatchUiState.Idle, viewModel: LiveMatchViewModel, onNavigateBack: () -> Unit, onOpenReport: (String, Boolean) -> Unit) {
    when (state.details.match.status) {
        MatchStatus.SCHEDULED -> PreKickoffContent(state, viewModel, onNavigateBack)
        MatchStatus.LIVE, MatchStatus.HALF_TIME -> LiveConsoleContent(state, viewModel, onNavigateBack)
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

@Composable
private fun LiveBadge() {
    Surface(shape = RoundedCornerShape(6.dp), color = LiveRed) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).background(Color.White, CircleShape))
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(Res.string.label_live_badge),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pre-kickoff (design 16A)
// ---------------------------------------------------------------------------------------------

@Composable
private fun PreKickoffContent(state: LiveMatchUiState.Idle, viewModel: LiveMatchViewModel, onNavigateBack: () -> Unit) {
    val details = state.details
    val canManage = details.canManageLiveMatch
    val requiredCount = details.team.teamSize ?: 11
    // Every call-up is CALLED_UP -- there's no other status to filter on (see CallUpStatus).
    val eligible = remember(details) { details.callUps }

    var halfLength by remember { mutableStateOf(30) }
    var startingIds by remember { mutableStateOf(emptySet<String>()) }

    val starting = eligible.filter { it.callUp.playerId in startingIds }
    val bench = eligible.filter { it.callUp.playerId !in startingIds }

    Column(Modifier.fillMaxSize()) {
        ConsoleHeader(
            title = "${details.team.name} vs ${details.opponent.name}",
            subtitle = matchSubtitle(details),
            onNavigateBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        // A one-half match is a single period: its "half length" is the match length.
                        stringResource(if (state.details.match.halves == 1) Res.string.label_match_length else Res.string.label_half_length).uppercase(),
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(8.dp))
                    // Scrolls sideways when the options don't fit (a narrow phone, a large font size).
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HALF_LENGTH_OPTIONS.forEach { option ->
                            HalfLengthPill(option, option == halfLength) { halfLength = option }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(Res.string.label_starting_xi).uppercase(),
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        stringResource(Res.string.label_players_selected, starting.size),
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            items(starting, key = { it.callUp.id }) { callUp ->
                CallUpToggleRow(callUp.player, selected = true, onClick = if (canManage) {
                    { startingIds = startingIds - callUp.callUp.playerId }
                } else null)
            }

            if (bench.isNotEmpty()) {
                item {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(Res.string.label_bench).uppercase(),
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(Res.string.msg_tap_to_add_to_xi),
                            color = Color.White.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                items(bench, key = { it.callUp.id }) { callUp ->
                    CallUpToggleRow(callUp.player, selected = false, onClick = if (canManage) {
                        { startingIds = startingIds + callUp.callUp.playerId }
                    } else null)
                }
            }

            if (eligible.isEmpty()) {
                item {
                    Text(
                        stringResource(Res.string.msg_no_callups),
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            if (canManage) {
                Button(
                    onClick = { viewModel.onKickOff(starting.map { it.callUp.playerId }, halfLength) },
                    enabled = starting.size == requiredCount && !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
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
                        Text(
                            stringResource(Res.string.btn_kick_off).uppercase(),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                val remaining = requiredCount - starting.size
                Text(
                    text = if (remaining > 0) {
                        stringResource(Res.string.msg_pick_more_to_kick_off, remaining, halfLength)
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
}

@Composable
private fun HalfLengthPill(minutes: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) PitchGreen else CarbonGray
    ) {
        Text(
            minutes.toString(),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun CallUpToggleRow(player: Player, selected: Boolean, onClick: (() -> Unit)?) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = if (selected) PitchGreen.copy(alpha = 0.18f) else CarbonGray,
        border = if (selected) BorderStroke(1.dp, PitchGreen) else null
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerBadge(
                player.photoUrl, player.shirtNumber,
                numberColor = if (selected) PitchGreen else Color.White.copy(alpha = 0.6f),
                placeholderColor = Color.White.copy(alpha = 0.4f),
                numberStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${player.firstName} ${player.lastName}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                player.position?.let {
                    Text(stringResource(it.labelRes()), color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (selected) {
                Box(Modifier.size(22.dp).background(PitchGreen, CircleShape), contentAlignment = Alignment.Center) {
                    Text("✓", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
            } else {
                Text("+", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun matchSubtitle(details: MatchDetails): String {
    val time = timeOfDay(details.match.dateTime)
    return details.match.location?.takeIf { it.isNotBlank() }
        ?.let { stringResource(Res.string.msg_kickoff_time_location, time, it) }
        ?: stringResource(Res.string.msg_kickoff_time_only, time)
}

private fun timeOfDay(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
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
}

/** "25+2'" / "25'": where the first half ended, against its nominal length. */
private fun firstHalfLabel(halfLength: Int, endMinute: Int): String =
    if (endMinute > halfLength) "$halfLength+${endMinute - halfLength}'" else "$endMinute'"

@Composable
private fun LiveConsoleContent(state: LiveMatchUiState.Idle, viewModel: LiveMatchViewModel, onNavigateBack: () -> Unit) {
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
            viewModel.clearFlash()
        }
    }

    // A clock correction is server-persisted (onSetMinute) -- once applied, this recomputes straight
    // from the corrected details.match like any other change.
    // Counted on the server's clock (see LiveMatchViewModel.clockOffset), since every match timestamp is the server's.
    val clockOffset by viewModel.clockOffset.collectAsStateWithLifecycle()
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
    val clockArea: @Composable (large: Boolean) -> Unit = { large ->
        if (status == MatchStatus.HALF_TIME) {
            // At half time there's nothing to pause or correct (36c).
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(Res.string.label_half_time_badge),
                    color = Color.White,
                    style = (if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium).copy(fontWeight = FontWeight.Black)
                )
                Text(
                    stringResource(Res.string.lm_half_time_sub, firstHalfLabel(halfLength, firstHalfEnd)),
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        } else {
            ClockBlock(
                clock = formatMatchClock(displaySeconds),
                label = if (large && canManage && !isPaused) "$clockLabel · ${stringResource(Res.string.lm_tap_to_correct)}" else clockLabel,
                isPaused = isPaused,
                canManage = canManage,
                enabled = !state.isSubmitting,
                large = large,
                onClockClick = { showClockSheet = true },
                onPauseToggle = { if (isPaused) viewModel.onResumeClock() else viewModel.onPauseClock() }
            )
        }
    }
    val scoreRow: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // Each side's logo before its name, only when it has one.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    details.clubLogoUrl?.let { NameCrest(it, details.team.name) }
                    Text(details.team.name, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    details.opponent.logoUrl?.let { NameCrest(it, details.opponent.name) }
                    Text(details.opponent.shortName ?: details.opponent.name, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            ScoreBox(state.score.ourScore, PitchGreen, enabled = scoreEnabled) { viewModel.onLogGoal(displayMinute, isOpponent = false) }
            Spacer(Modifier.width(6.dp))
            ScoreBox(state.score.opponentScore, CarbonGray, enabled = scoreEnabled) { viewModel.onLogGoal(displayMinute, isOpponent = true) }
        }
    }
    val tabs: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TabPill(stringResource(Res.string.tab_squad), state.selectedTab == LiveMatchTab.SQUAD) { viewModel.onTabSelected(LiveMatchTab.SQUAD) }
            TabPill(stringResource(Res.string.tab_events_count, state.events.size), state.selectedTab == LiveMatchTab.EVENTS) { viewModel.onTabSelected(LiveMatchTab.EVENTS) }
        }
    }
    val tabContent: @Composable (sideBySide: Boolean) -> Unit = { sideBySide ->
        when (state.selectedTab) {
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
    val actionBar: @Composable (Modifier) -> Unit = { modifier ->
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
                            onClick = { viewModel.onResumeSecondHalf() },
                            enabled = !state.isSubmitting,
                            modifier = Modifier.weight(1f).height(52.dp),
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
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) { Text(stringResource(Res.string.btn_half_time), fontWeight = FontWeight.Bold) }
                            }
                            OutlinedButton(
                                onClick = { showFullTimeConfirm = true },
                                enabled = !state.isSubmitting,
                                modifier = Modifier.weight(1f).height(52.dp),
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
            // Landscape (36f): the status block is a left column with a larger clock and the match
            // controls; On pitch and Bench sit side by side on the right.
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(320.dp).fillMaxHeight()) {
                    ConsoleHeader(title = "${details.ageGroup.name} · ${details.season.name}", subtitle = null, onNavigateBack = onNavigateBack, trailing = { LiveBadge() })
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        scoreRow()
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { clockArea(true) }
                        tabs()
                    }
                    Spacer(Modifier.weight(1f))
                    actionBar(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp))
                }
                Box(Modifier.weight(1f).fillMaxHeight().statusBarsPadding()) { tabContent(true) }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ConsoleHeader(title = "${details.ageGroup.name} · ${details.season.name}", subtitle = null, onNavigateBack = onNavigateBack, trailing = { LiveBadge() })
                Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { scoreRow() }
                    Spacer(Modifier.width(10.dp))
                    clockArea(false)
                }
                Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) { tabs() }
                Box(Modifier.weight(1f).fillMaxWidth()) { tabContent(false) }
                actionBar(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp))
            }
        }
    }

    if (showClockSheet) {
        ClockCorrectionSheet(
            initialMinute = displayMinute,
            isPaused = isPaused,
            onSave = {
                viewModel.onSetMinute(it)
                showClockSheet = false
            },
            onDismiss = { showClockSheet = false }
        )
    }
    if (showHalfTimeConfirm) {
        ConfirmDialog(
            title = stringResource(Res.string.msg_confirm_half_time_title),
            body = stringResource(Res.string.msg_confirm_half_time_body),
            onConfirm = { showHalfTimeConfirm = false; viewModel.onHalfTime() },
            onDismiss = { showHalfTimeConfirm = false }
        )
    }
    if (showFullTimeConfirm) {
        ConfirmDialog(
            title = stringResource(Res.string.msg_confirm_full_time_title),
            body = stringResource(Res.string.msg_confirm_full_time_body),
            onConfirm = { showFullTimeConfirm = false; viewModel.onFullTime() },
            onDismiss = { showFullTimeConfirm = false }
        )
    }
    deletingEvent?.let { de ->
        ConfirmDialog(
            title = stringResource(Res.string.msg_confirm_delete_event_title),
            body = stringResource(Res.string.msg_confirm_delete_event_body),
            onConfirm = {
                when (de) {
                    is DisplayEvent.Simple -> viewModel.onDeleteEvent(de.event.id)
                    is DisplayEvent.Substitution -> viewModel.onDeleteSubstitution(de.out.id, de.into.id)
                }
                deletingEvent = null
            },
            onDismiss = { deletingEvent = null }
        )
    }
    actionSheet?.let { sheet ->
        PlayerActionSheet(
            sheet = sheet,
            squad = squad,
            minutes = minutes,
            minute = displayMinute,
            viewModel = viewModel,
            enabled = !state.isSubmitting,
            onDismiss = { actionSheet = null },
            onChangeSheet = { actionSheet = it }
        )
    }
}

@Composable
private fun ScoreBox(score: Int, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = color
    ) {
        Text(
            score.toString(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).widthIn(min = 20.dp),
            color = Color.White,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = if (selected) Color.White else CarbonGray) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (selected) DeepCarbon else Color.White,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
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
    squad: DerivedSquad,
    minutes: SquadMinutes,
    minute: Int,
    viewModel: LiveMatchViewModel,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onChangeSheet: (ActionSheet?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = CarbonGray) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp).navigationBarsPadding()) {
            when (sheet) {
                is ActionSheet.PlayerActions -> {
                    val name = "${sheet.row.player.firstName} ${sheet.row.player.lastName}"
                    Text(
                        stringResource(Res.string.title_log_event_for, name),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (sheet.onPitch) {
                        // A goal goes on to an optional "who assisted?" step; assists are only logged
                        // that way, attached to their goal.
                        SheetAction(stringResource(Res.string.action_log_goal), enabled = enabled) {
                            onChangeSheet(ActionSheet.PickAssist(sheet.row.player.id, name, minute))
                        }
                        SheetAction(stringResource(Res.string.action_log_yellow_card), enabled = enabled) {
                            viewModel.onLogEvent(EventType.YELLOW_CARD, sheet.row.player.id, minute); onDismiss()
                        }
                        SheetAction(stringResource(Res.string.action_log_red_card), enabled = enabled) {
                            viewModel.onLogEvent(EventType.RED_CARD, sheet.row.player.id, minute); onDismiss()
                        }
                        SheetAction(stringResource(Res.string.action_log_own_goal), enabled = enabled) {
                            viewModel.onLogEvent(EventType.OWN_GOAL, sheet.row.player.id, minute); onDismiss()
                        }
                        if (squad.bench.isNotEmpty()) {
                            SheetAction(stringResource(Res.string.action_substitute_off), enabled = enabled) {
                                onChangeSheet(ActionSheet.PickComingOn(sheet.row.player))
                            }
                        }
                    } else if (squad.onPitch.isNotEmpty()) {
                        SheetAction(stringResource(Res.string.action_substitute_on), enabled = enabled) {
                            onChangeSheet(ActionSheet.PickGoingOff(sheet.row.player))
                        }
                    }
                }

                is ActionSheet.PickComingOn -> SubstitutionPicker(sheet.outgoing, takingOff = true, candidates = squad.bench, minutes = minutes, enabled = enabled) { incoming ->
                    viewModel.onSubstitute(sheet.outgoing.id, incoming.id, minute); onDismiss()
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
                        viewModel.onLogPlayerGoal(sheet.scorerId, assistPlayerId = null, minute = sheet.minute); onDismiss()
                    }
                    squad.onPitch.filter { it.player.id != sheet.scorerId }.forEach { row ->
                        SheetAction(pickerLabel(row.player), enabled = enabled) {
                            viewModel.onLogPlayerGoal(sheet.scorerId, assistPlayerId = row.player.id, minute = sheet.minute); onDismiss()
                        }
                    }
                }

                is ActionSheet.PickGoingOff -> SubstitutionPicker(sheet.incoming, takingOff = false, candidates = squad.onPitch, minutes = minutes, enabled = enabled) { outgoing ->
                    viewModel.onSubstitute(outgoing.id, sheet.incoming.id, minute); onDismiss()
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
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.msg_no_events_yet), color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
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
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                details.team.name,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${state.score.ourScore}-${state.score.opponentScore}",
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
            Text(details.opponent.name, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
        }

        // Design 36e: the goals in one line, then everyone's minutes -- the spread at a glance before
        // leaving the pitch -- and straight into the match report, which carries the same minutes.
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
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
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp).height(52.dp),
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
