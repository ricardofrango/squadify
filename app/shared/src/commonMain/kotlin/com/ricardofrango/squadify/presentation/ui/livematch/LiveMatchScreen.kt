package com.ricardofrango.squadify.presentation.ui.livematch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchCallUpWithPlayer
import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
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
private val HALF_LENGTH_OPTIONS = listOf(20, 25, 30, 40)

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
    viewModel: LiveMatchViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(matchId) { viewModel.load(matchId) }

    // Insets are handled by hand below (ConsoleHeader's statusBarsPadding, the bottom action bars'
    // navigationBarsPadding) since the header/bottom bar are plain content, not Scaffold's topBar/
    // bottomBar slots. Scaffold's own default contentWindowInsets would apply the same system-bar
    // insets a second time on top of those -- pushing the header down and the bottom button up.
    Scaffold(containerColor = DeepCarbon, contentWindowInsets = WindowInsets(0)) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues).background(DeepCarbon)) {
            when (val state = uiState) {
                is LiveMatchUiState.Loading -> LoadingBody(onNavigateBack)
                is LiveMatchUiState.Error -> ErrorBody(state.message, onNavigateBack)
                is LiveMatchUiState.Idle -> IdleBody(state, viewModel, onNavigateBack)
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
private fun IdleBody(state: LiveMatchUiState.Idle, viewModel: LiveMatchViewModel, onNavigateBack: () -> Unit) {
    when (state.details.match.status) {
        MatchStatus.SCHEDULED -> PreKickoffContent(state, viewModel, onNavigateBack)
        MatchStatus.LIVE, MatchStatus.HALF_TIME -> LiveConsoleContent(state, viewModel, onNavigateBack)
        MatchStatus.FINISHED, MatchStatus.CANCELLED -> SummaryContent(state, onNavigateBack)
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
    val requiredCount = details.team.teamSize ?: 11
    val eligible = remember(details) {
        details.callUps.filter { it.callUp.status == CallUpStatus.CALLED_UP || it.callUp.status == CallUpStatus.CONFIRMED }
    }

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
                        stringResource(Res.string.label_half_length).uppercase(),
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                CallUpToggleRow(callUp.player, selected = true) {
                    startingIds = startingIds - callUp.callUp.playerId
                }
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
                    CallUpToggleRow(callUp.player, selected = false) {
                        startingIds = startingIds + callUp.callUp.playerId
                    }
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
        }
    }
}

@Composable
private fun HalfLengthPill(minutes: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
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
private fun CallUpToggleRow(player: Player, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = if (selected) PitchGreen.copy(alpha = 0.18f) else CarbonGray,
        border = if (selected) BorderStroke(1.dp, PitchGreen) else null
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                player.shirtNumber?.toString() ?: "-",
                color = if (selected) PitchGreen else Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.width(28.dp)
            )
            Text(
                "${player.firstName} ${player.lastName}",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

/**
 * Elapsed match time in seconds, derived from the match's persisted kickoff/second-half timestamps --
 * plain Instant subtraction, so it's correct regardless of the device's timezone (there's no local
 * wall-clock math involved at all). Continues from the first half's nominal length once the second
 * half has started, rather than restarting at 0. Returns 0 before kickoff.
 */
private fun elapsedMatchSeconds(match: Match): Int {
    val now = Clock.System.now()
    val secondHalfStart = match.secondHalfStartedAt
    return if (secondHalfStart != null) {
        val halfLengthSeconds = (match.halfLengthMinutes ?: 0) * 60
        halfLengthSeconds + (now - secondHalfStart).inWholeSeconds.toInt()
    } else {
        val kickoff = match.kickoffAt ?: return 0
        (now - kickoff).inWholeSeconds.toInt()
    }
}

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
    data class PickComingOn(val outgoingId: String, val outgoingName: String) : ActionSheet()
    data class PickGoingOff(val incomingId: String, val incomingName: String) : ActionSheet()
}

@Composable
private fun LiveConsoleContent(state: LiveMatchUiState.Idle, viewModel: LiveMatchViewModel, onNavigateBack: () -> Unit) {
    val details = state.details
    val status = details.match.status

    // The clock is derived from the match's persisted kickoffAt/secondHalfStartedAt timestamps (see
    // elapsedMatchSeconds), not a local counter -- so it survives leaving and returning to this screen
    // and is correct regardless of the device's timezone (Instant arithmetic is absolute). `tick`
    // exists only to force a recomposition every second; it isn't itself the clock.
    val isSecondHalf = details.match.secondHalfStartedAt != null
    var showSetMinute by remember { mutableStateOf(false) }
    var showHalfTimeConfirm by remember { mutableStateOf(false) }
    var showFullTimeConfirm by remember { mutableStateOf(false) }
    var actionSheet by remember { mutableStateOf<ActionSheet?>(null) }
    var deletingEvent by remember { mutableStateOf<DisplayEvent?>(null) }
    // A one-off "Set minute" nudge on top of the wall-clock elapsed time -- resets when the screen is
    // reopened (recomputes fresh from the persisted timestamps instead), which is the intended
    // behaviour: the clock is server-derived truth, not a locally remembered correction.
    var manualOffsetSeconds by remember(details.match.id) { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(status) {
        if (status == MatchStatus.LIVE) {
            while (true) {
                delay(1_000)
                tick++
            }
        }
    }

    val displaySeconds = remember(tick, details.match, manualOffsetSeconds) {
        elapsedMatchSeconds(details.match) + manualOffsetSeconds
    }
    val displayMinute = displaySeconds / 60

    val squad = remember(details, state.events) { deriveSquad(details, state.events) }
    val displayEvents = remember(state.events) { buildDisplayEvents(state.events) }
    val ownUnattributed = state.events.count { it.type == EventType.GOAL && !it.isOpponent && it.playerId == null }

    Column(Modifier.fillMaxSize()) {
        ConsoleHeader(
            title = "${details.ageGroup.name} · ${details.season.name}",
            subtitle = null,
            onNavigateBack = onNavigateBack,
            trailing = { LiveBadge() }
        )

        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        details.team.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        details.opponent.shortName ?: details.opponent.name,
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ScoreBox(state.score.ourScore, PitchGreen, enabled = status == MatchStatus.LIVE && !state.isSubmitting) {
                    viewModel.onLogGoal(displayMinute, isOpponent = false)
                }
                Spacer(Modifier.width(8.dp))
                ScoreBox(state.score.opponentScore, CarbonGray, enabled = status == MatchStatus.LIVE && !state.isSubmitting) {
                    viewModel.onLogGoal(displayMinute, isOpponent = true)
                }
                Spacer(Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.End) {
                    if (status == MatchStatus.HALF_TIME) {
                        Text(
                            stringResource(Res.string.label_half_time_badge),
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    } else {
                        Text(
                            formatMatchClock(displaySeconds),
                            color = PitchGreen,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            stringResource(if (isSecondHalf) Res.string.label_second_half else Res.string.label_first_half).uppercase(),
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val ownGoalsNote = if (ownUnattributed == 0) {
                    stringResource(Res.string.msg_own_goals_none)
                } else {
                    stringResource(Res.string.msg_own_goals_count, ownUnattributed)
                }
                Text(
                    "${stringResource(Res.string.msg_score_tap_hint)} · $ownGoalsNote",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(Res.string.label_set_minute),
                    color = PitchGreen,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showSetMinute = true }
                    )
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TabPill(stringResource(Res.string.tab_squad), state.selectedTab == LiveMatchTab.SQUAD) {
                viewModel.onTabSelected(LiveMatchTab.SQUAD)
            }
            TabPill(
                stringResource(Res.string.tab_events_count, state.events.size),
                state.selectedTab == LiveMatchTab.EVENTS
            ) {
                viewModel.onTabSelected(LiveMatchTab.EVENTS)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state.selectedTab) {
                LiveMatchTab.SQUAD -> SquadTab(squad) { row, onPitch -> actionSheet = ActionSheet.PlayerActions(row, onPitch) }
                LiveMatchTab.EVENTS -> EventsTab(displayEvents, details) { deletingEvent = it }
            }
        }

        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (status == MatchStatus.HALF_TIME) {
                    Button(
                        onClick = { viewModel.onResumeSecondHalf() },
                        enabled = !state.isSubmitting,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White)
                    ) {
                        Text(stringResource(Res.string.btn_start_second_half), fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { showHalfTimeConfirm = true },
                        enabled = !state.isSubmitting,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text(stringResource(Res.string.btn_half_time), fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = { showFullTimeConfirm = true },
                    enabled = !state.isSubmitting,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, LiveRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveRed)
                ) {
                    Text(stringResource(Res.string.btn_full_time), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showSetMinute) {
        SetMinuteDialog(
            initial = displayMinute,
            onConfirm = {
                manualOffsetSeconds = it * 60 - elapsedMatchSeconds(details.match)
                showSetMinute = false
            },
            onDismiss = { showSetMinute = false }
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(min = 20.dp),
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

@Composable
private fun SetMinuteDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.label_set_minute)) },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (value > 0) value-- }) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                Text(
                    formatMatchClock(value * 60),
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = { value++ }) { Text("+", style = MaterialTheme.typography.headlineSmall) }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(stringResource(Res.string.btn_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.btn_cancel)) } }
    )
}

@Composable
private fun ConfirmDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.btn_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.btn_cancel)) } }
    )
}

// ---------------------------------------------------------------------------------------------
// Squad tab: on pitch / bench / sent off, derived from the starting XI + the event log
// ---------------------------------------------------------------------------------------------

private data class SquadPlayerRow(val player: Player, val goals: Int, val hasYellow: Boolean)
private data class DerivedSquad(
    val onPitch: List<SquadPlayerRow>,
    val bench: List<SquadPlayerRow>,
    val sentOff: List<SquadPlayerRow>
)

private fun deriveSquad(details: MatchDetails, events: List<MatchEvent>): DerivedSquad {
    val callUps = details.callUps
    val onPitchIds = callUps.filter { it.callUp.isStarting }.map { it.callUp.playerId }.toMutableList()
    val benchIds = callUps
        .filter { !it.callUp.isStarting && (it.callUp.status == CallUpStatus.CALLED_UP || it.callUp.status == CallUpStatus.CONFIRMED) }
        .map { it.callUp.playerId }
        .toMutableList()
    val sentOffIds = mutableListOf<String>()

    val ordered = events.sortedWith(compareBy({ it.minute }, { it.createdAt }))
    for (event in ordered) {
        val playerId = event.playerId ?: continue
        when (event.type) {
            EventType.SUBSTITUTION_OUT -> if (onPitchIds.remove(playerId)) benchIds.add(playerId)
            EventType.SUBSTITUTION_IN -> if (benchIds.remove(playerId)) onPitchIds.add(playerId)
            EventType.RED_CARD -> if (!event.isOpponent) {
                onPitchIds.remove(playerId)
                benchIds.remove(playerId)
                if (playerId !in sentOffIds) sentOffIds.add(playerId)
            }
            else -> {}
        }
    }

    val playersById = callUps.associate { it.callUp.playerId to it.player }
    fun rowsFor(ids: List<String>) = ids.mapNotNull { id ->
        val player = playersById[id] ?: return@mapNotNull null
        val goals = ordered.count { it.playerId == id && !it.isOpponent && it.type == EventType.GOAL }
        val hasYellow = ordered.any { it.playerId == id && !it.isOpponent && it.type == EventType.YELLOW_CARD }
        SquadPlayerRow(player, goals, hasYellow)
    }

    return DerivedSquad(rowsFor(onPitchIds), rowsFor(benchIds), rowsFor(sentOffIds))
}

@Composable
private fun SquadTab(squad: DerivedSquad, onPlayerClick: (SquadPlayerRow, Boolean) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                stringResource(Res.string.msg_squad_tap_hint),
                color = Color.White.copy(alpha = 0.4f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        item {
            Text(
                stringResource(Res.string.label_on_pitch_count, squad.onPitch.size).uppercase(),
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
        items(squad.onPitch, key = { it.player.id }) { row ->
            SquadRow(row, SquadRowStyle.ON_PITCH) { onPlayerClick(row, true) }
        }
        if (squad.bench.isNotEmpty()) {
            item {
                Text(
                    stringResource(Res.string.label_bench).uppercase(),
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(squad.bench, key = { it.player.id }) { row ->
                SquadRow(row, SquadRowStyle.BENCH) { onPlayerClick(row, false) }
            }
        }
        if (squad.sentOff.isNotEmpty()) {
            item {
                Text(
                    stringResource(Res.string.label_sent_off).uppercase(),
                    color = LiveRed,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(squad.sentOff, key = { it.player.id }) { row ->
                SquadRow(row, SquadRowStyle.SENT_OFF, onClick = null)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

private enum class SquadRowStyle { ON_PITCH, BENCH, SENT_OFF }

@Composable
private fun SquadRow(row: SquadPlayerRow, style: SquadRowStyle, onClick: (() -> Unit)?) {
    val bg = when (style) {
        SquadRowStyle.ON_PITCH -> PitchGreen.copy(alpha = 0.18f)
        SquadRowStyle.BENCH -> CarbonGray
        SquadRowStyle.SENT_OFF -> LiveRed.copy(alpha = 0.12f)
    }
    val border = when (style) {
        SquadRowStyle.ON_PITCH -> BorderStroke(1.dp, PitchGreen.copy(alpha = 0.4f))
        SquadRowStyle.SENT_OFF -> BorderStroke(1.dp, LiveRed.copy(alpha = 0.4f))
        SquadRowStyle.BENCH -> null
    }
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = bg,
        border = border
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.player.shirtNumber?.toString() ?: "-",
                color = if (style == SquadRowStyle.SENT_OFF) LiveRed else PitchGreen,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.width(28.dp)
            )
            Text(
                "${row.player.firstName} ${row.player.lastName}",
                color = if (style == SquadRowStyle.SENT_OFF) Color.White.copy(alpha = 0.5f) else Color.White,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (style == SquadRowStyle.SENT_OFF) TextDecoration.LineThrough else TextDecoration.None
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (row.goals > 0) {
                Surface(shape = RoundedCornerShape(6.dp), color = PitchGreen.copy(alpha = 0.2f)) {
                    Text(
                        stringResource(Res.string.label_goals_count, row.goals),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = PitchGreen,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            if (row.hasYellow) {
                Box(Modifier.size(width = 14.dp, height = 18.dp).background(CardYellow, RoundedCornerShape(2.dp)))
            }
            if (style == SquadRowStyle.SENT_OFF) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(width = 14.dp, height = 18.dp).background(LiveRed, RoundedCornerShape(2.dp)))
            }
        }
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
    minute: Int,
    viewModel: LiveMatchViewModel,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onChangeSheet: (ActionSheet?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = CarbonGray) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).navigationBarsPadding()) {
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
                        SheetAction(stringResource(Res.string.action_log_goal), enabled = enabled) {
                            viewModel.onLogEvent(EventType.GOAL, sheet.row.player.id, minute); onDismiss()
                        }
                        SheetAction(stringResource(Res.string.action_log_assist), enabled = enabled) {
                            viewModel.onLogEvent(EventType.ASSIST, sheet.row.player.id, minute); onDismiss()
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
                                onChangeSheet(ActionSheet.PickComingOn(sheet.row.player.id, name))
                            }
                        }
                    } else if (squad.onPitch.isNotEmpty()) {
                        SheetAction(stringResource(Res.string.action_substitute_on), enabled = enabled) {
                            onChangeSheet(ActionSheet.PickGoingOff(sheet.row.player.id, name))
                        }
                    }
                }

                is ActionSheet.PickComingOn -> {
                    Text(
                        stringResource(Res.string.title_pick_player_coming_on, sheet.outgoingName),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    squad.bench.forEach { row ->
                        SheetAction("${row.player.shirtNumber ?: "-"} · ${row.player.firstName} ${row.player.lastName}", enabled = enabled) {
                            viewModel.onSubstitute(sheet.outgoingId, row.player.id, minute); onDismiss()
                        }
                    }
                }

                is ActionSheet.PickGoingOff -> {
                    Text(
                        stringResource(Res.string.title_pick_player_going_off, sheet.incomingName),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    squad.onPitch.forEach { row ->
                        SheetAction("${row.player.shirtNumber ?: "-"} · ${row.player.firstName} ${row.player.lastName}", enabled = enabled) {
                            viewModel.onSubstitute(row.player.id, sheet.incomingId, minute); onDismiss()
                        }
                    }
                }
            }
        }
    }
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

    data class Simple(val event: MatchEvent) : DisplayEvent() {
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
    val simples = events.filter { it.id !in pairedIds }.map { DisplayEvent.Simple(it) }
    return (paired + simples).sortedWith(compareByDescending<DisplayEvent> { it.minute }.thenByDescending { it.createdAt })
}

@Composable
private fun EventsTab(displayEvents: List<DisplayEvent>, details: MatchDetails, onDeleteRequest: (DisplayEvent) -> Unit) {
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
            EventRow(de, details, onDelete = { onDeleteRequest(de) })
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun EventRow(de: DisplayEvent, details: MatchDetails, onDelete: (() -> Unit)?) {
    val playersById = remember(details) { details.callUps.associate { it.callUp.playerId to it.player } }
    fun nameFor(event: MatchEvent) = event.playerId?.let { playersById[it] }?.let { "${it.firstName} ${it.lastName}" } ?: "-"

    val (glyph, glyphColor, text) = when (de) {
        is DisplayEvent.Simple -> eventLineFor(de.event, details, ::nameFor)
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
private fun eventLineFor(event: MatchEvent, details: MatchDetails, nameFor: (MatchEvent) -> String): Triple<String, Color, String> {
    val ourName = event.playerId?.let { nameFor(event) }
    val opponentName = details.opponent.shortName ?: details.opponent.name
    return when (event.type) {
        EventType.GOAL -> when {
            event.isOpponent -> Triple("•", Color.White.copy(alpha = 0.6f), stringResource(Res.string.label_event_goal, opponentName))
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
private fun SummaryContent(state: LiveMatchUiState.Idle, onNavigateBack: () -> Unit) {
    val details = state.details
    val displayEvents = remember(state.events) { buildDisplayEvents(state.events) }
    val outcome: Pair<org.jetbrains.compose.resources.StringResource, Color> = when {
        details.match.status == MatchStatus.CANCELLED -> Res.string.label_result_cancelled to Color.White.copy(alpha = 0.5f)
        state.score.ourScore > state.score.opponentScore -> Res.string.label_result_win to PitchGreen
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

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayEvents, key = { it.key }) { de -> EventRow(de, details, onDelete = null) }
            item { MinutesPlayedRow() }
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

@Composable
private fun MinutesPlayedRow() {
    // Not built out yet -- the per-player minutes-played breakdown has no design (mentioned as a
    // future screen). Shown inert so the affordance from the mock is visible without promising a
    // destination that doesn't exist, mirroring TeamDetailsScreen's inert "Edit team" row.
    Surface(modifier = Modifier.fillMaxWidth(), shape = CardShape, color = CarbonGray) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.label_minutes_played),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    stringResource(Res.string.msg_minutes_played_subtitle),
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text("›", color = Color.White.copy(alpha = 0.3f), style = MaterialTheme.typography.titleMedium)
        }
    }
}
