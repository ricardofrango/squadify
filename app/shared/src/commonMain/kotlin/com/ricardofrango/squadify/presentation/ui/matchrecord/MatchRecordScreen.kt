package com.ricardofrango.squadify.presentation.ui.matchrecord

import com.ricardofrango.squadify.presentation.ui.components.readableListPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.ourSideFirst
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import com.ricardofrango.squadify.presentation.ui.components.FilterChipRow
import com.ricardofrango.squadify.presentation.ui.components.PinnedBottomBar
import com.ricardofrango.squadify.presentation.ui.components.PlayerBadge
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.components.SectionCard
import com.ricardofrango.squadify.presentation.ui.components.SegmentedFilter
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.livematch.DisplayEvent
import com.ricardofrango.squadify.presentation.ui.livematch.buildDisplayEvents
import com.ricardofrango.squadify.presentation.ui.matchdetails.CallUpSort
import com.ricardofrango.squadify.presentation.ui.matchdetails.RecordEventRow
import com.ricardofrango.squadify.presentation.ui.matchdetails.SectionHeader
import com.ricardofrango.squadify.presentation.ui.matchdetails.previewFinishedDetails
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.matchdetails.LossRed
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.*
import kotlin.time.Instant

private val FieldShape = RoundedCornerShape(14.dp)

/**
 * A match's record after the game (TECHNICAL_DECISIONS.md §70), from Match details: a finished game's
 * goals and cards corrected, or a game that wasn't followed live entered with its minutes ("Enter
 * result"). Nothing is sent until Save.
 */
@Composable
fun MatchRecordScreen(
    matchId: String,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: MatchRecordViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val state = viewModel.state.collectSettledState()
        LaunchedEffect(matchId) { viewModel.load(matchId) }
        LaunchedEffect(Unit) { viewModel.saved.collect { onSaved() } }
        MatchRecordContent(
            state = state,
            onNavigateBack = onNavigateBack,
            onRetry = { viewModel.load(matchId) },
            onSaveEvent = viewModel::onSaveEvent,
            onDeleteEvent = viewModel::onDeleteEvent,
            onHalfLength = viewModel::onHalfLength,
            onMinutes = viewModel::onMinutes,
            onFullMatch = viewModel::onFullMatch,
            onSort = viewModel::onSort,
            onSave = viewModel::save
        )
    }
}

@Composable
private fun MatchRecordContent(
    state: MatchRecordUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onSaveEvent: (DraftEvent) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onHalfLength: (Int) -> Unit,
    onMinutes: (String, String) -> Unit,
    onFullMatch: (String) -> Unit,
    onSort: (CallUpSort) -> Unit,
    onSave: () -> Unit
) {
    val idle = state as? MatchRecordUiState.Idle
    val firstEntry = idle?.details?.match?.status == MatchStatus.SCHEDULED
    // The event being added (a blank key) or edited in the sheet.
    var editing by remember { mutableStateOf<DraftEvent?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    val unsaved = idle?.dirty == true
    val requestBack = { if (unsaved) showDiscard = true else onNavigateBack() }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = unsaved,
        onBackCompleted = { showDiscard = true }
    )

    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            SquadifyTopBar(
                title = stringResource(if (firstEntry) Res.string.title_enter_result else Res.string.title_match_record),
                subtitle = idle?.details?.let { "${it.team.name} vs ${it.opponent.shortName ?: it.opponent.name}" },
                onNavigateBack = requestBack
            )
        },
        bottomBar = {
            if (idle != null) PinnedBottomBar {
                val message = idle.saveError ?: idle.problem?.let { stringResource(it.messageRes()) }
                if (message != null) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                PrimaryLoadingButton(
                    text = if (firstEntry) Res.string.btn_save_result else Res.string.btn_save_changes,
                    submittingText = Res.string.btn_saving_changes,
                    isLoading = idle.saving,
                    enabled = idle.canSave,
                    onClick = onSave
                )
            }
        }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBackground)
        when (state) {
            MatchRecordUiState.Loading -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PitchGreen) }
            is MatchRecordUiState.Error -> Column(modifier.screenContentPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text(stringResource(Res.string.btn_retry)) }
            }
            is MatchRecordUiState.Idle -> RecordBody(
                state, modifier,
                onAddEvent = { editing = blankEvent() },
                onEditEvent = { editing = it },
                onHalfLength = onHalfLength,
                onMinutes = onMinutes,
                onFullMatch = onFullMatch,
                onSort = onSort,
                onSave = onSave
            )
        }
    }

    val event = editing
    if (event != null && idle != null) {
        EventSheet(
            initial = event,
            state = idle,
            onSave = { onSaveEvent(it); editing = null },
            onDelete = if (event.key.isNotEmpty()) ({ onDeleteEvent(event.key); editing = null }) else null,
            onDismiss = { editing = null }
        )
    }
    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            containerColor = Color.White,
            titleContentColor = DeepCarbon,
            textContentColor = DeepCarbon.copy(alpha = 0.75f),
            title = { Text(stringResource(Res.string.club_discard_title)) },
            text = { Text(stringResource(Res.string.msg_record_discard)) },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; onNavigateBack() }) {
                    Text(stringResource(Res.string.club_discard_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text(stringResource(Res.string.btn_keep_editing), color = CarbonGray) } }
        )
    }
}

private fun blankEvent() = DraftEvent(key = "", id = null, type = EventType.GOAL, minute = 0, period = 1, playerId = null, isOpponent = false)

private fun RecordProblem.messageRes(): StringResource = when (this) {
    RecordProblem.MINUTES_INVALID -> Res.string.msg_record_minutes_invalid
    RecordProblem.SCORER_DIDNT_PLAY -> Res.string.msg_record_scorer_didnt_play
}

@Composable
private fun RecordBody(
    state: MatchRecordUiState.Idle,
    modifier: Modifier,
    onAddEvent: () -> Unit,
    onEditEvent: (DraftEvent) -> Unit,
    onHalfLength: (Int) -> Unit,
    onMinutes: (String, String) -> Unit,
    onFullMatch: (String) -> Unit,
    onSort: (CallUpSort) -> Unit,
    onSave: () -> Unit
) {
    val details = state.details
    val rows = remember(state.events) {
        val byKey = state.events.associateBy { it.key }
        buildDisplayEvents(state.events.asMatchEvents(details.match.id, Instant.fromEpochMilliseconds(0)))
            .filterIsInstance<DisplayEvent.Simple>()
            .mapNotNull { row -> byKey[row.event.id]?.let { row to it } }
            .sortedWith(compareBy({ it.second.period }, { it.second.minute }))
    }
    val players = remember(details, state.sort) { minutesOrder(details.callUps.map { it.player }, state.sort) }
    // Centred on a wide screen, the whole width still scrolling.
    BoxWithConstraints(modifier) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = readableListPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScoreCard(state) }
        if (!state.afterGame) {
            item {
                Text(stringResource(Res.string.msg_record_live_kept), style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.6f))
            }
        } else {
            item { SectionHeader(stringResource(Res.string.label_half_length)) }
            item {
                // The whole bar runs past the screen's padding and scrolls sideways, rather than
                // scrolling inside its outline.
                SegmentedFilter(
                    options = RECORD_HALF_LENGTHS,
                    selected = state.halfLength,
                    label = { stringResource(Res.string.stats_minutes_value, it) },
                    onSelect = onHalfLength,
                    shorten = false,
                    insidePaddedContent = true
                )
            }
        }
        item { SectionHeader(stringResource(Res.string.md_tab_events), trailing = stringResource(Res.string.btn_add_event) to onAddEvent) }
        if (rows.isEmpty()) {
            item { Text(stringResource(Res.string.msg_record_no_events), style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.5f)) }
        }
        items(rows, key = { it.second.key }) { (row, draft) ->
            RecordEventRow(row, details, onClick = { onEditEvent(draft) })
        }
        if (state.afterGame) {
            item {
                SectionHeader(
                    stringResource(Res.string.label_minutes_played),
                    pill = stringResource(Res.string.label_match_length_value, state.matchLength)
                )
            }
            item {
                FilterChipRow(
                    options = listOf(CallUpSort.NAME, CallUpSort.POSITION),
                    selected = state.sort,
                    label = { stringResource(if (it == CallUpSort.POSITION) Res.string.label_position_field else Res.string.label_sort_name) },
                    onSelect = onSort,
                    insidePaddedContent = true,
                    leadingLabel = stringResource(Res.string.label_sort)
                )
            }
            item {
                SectionCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    players.forEachIndexed { i, player ->
                        MinutesRow(
                            player, state.minutes[player.id].orEmpty(), state.matchLength,
                            enabled = !state.saving,
                            last = i == players.lastIndex,
                            onChange = { onMinutes(player.id, it) },
                            onFullMatch = { onFullMatch(player.id) },
                            onSave = onSave
                        )
                    }
                }
            }
        }
    }
    }
}

/** The score the events make, as football writes it: the home side first. */
@Composable
private fun ScoreCard(state: MatchRecordUiState.Idle) {
    val details = state.details
    val (ours, theirs) = draftScore(state.events)
    val us = details.team.name
    val them = details.opponent.shortName ?: details.opponent.name
    val ourFirst = details.match.venue.ourSideFirst()
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (ourFirst) us else them, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
            Text(
                if (ourFirst) "$ours – $theirs" else "$theirs – $ours",
                Modifier.padding(horizontal = 14.dp),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                color = DeepCarbon
            )
            Text(if (ourFirst) them else us, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MinutesRow(
    player: Player,
    text: String,
    matchLength: Int,
    enabled: Boolean,
    last: Boolean,
    onChange: (String) -> Unit,
    onFullMatch: () -> Unit,
    onSave: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        PlayerBadge(player.photoUrl, player.shirtNumber, photoSize = 30.dp)
        Spacer(Modifier.width(8.dp))
        Text("${player.firstName} ${player.lastName}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // Most of a team plays the whole game: one tap instead of typing it.
        if (text != matchLength.toString()) {
            TextButton(onClick = onFullMatch) { Text(stringResource(Res.string.btn_full_match), color = PitchGreen, style = MaterialTheme.typography.labelMedium) }
        }
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            modifier = Modifier.width(76.dp),
            singleLine = true,
            placeholder = { Text("0", color = DeepCarbon.copy(alpha = 0.3f)) },
            suffix = { Text("'", color = DeepCarbon.copy(alpha = 0.5f)) },
            enabled = enabled,
            // Next moves to the following player's box; the last one's action saves (when Save is
            // enabled; otherwise it does nothing and the keyboard stays), as on Rate players.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = if (last) ImeAction.Done else ImeAction.Next),
            keyboardActions = if (last) KeyboardActions(onDone = { onSave() }) else KeyboardActions.Default,
            isError = text.isNotBlank() && (text.toIntOrNull() ?: -1) !in 0..matchLength,
            shape = FieldShape,
            colors = fieldColors()
        )
    }
}

// ---- The event sheet ----

/** Adds or edits one goal or card: what, whose, who (and who assisted), the minute and the half. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventSheet(
    initial: DraftEvent,
    state: MatchRecordUiState.Idle,
    onSave: (DraftEvent) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val details = state.details
    var draft by remember { mutableStateOf(initial) }
    var minuteText by remember { mutableStateOf(if (initial.key.isEmpty()) "" else initial.minute.toString()) }
    // The half follows the minute typed until it's picked by hand (first-half stoppage time).
    var periodPicked by remember { mutableStateOf(false) }
    val kind = draft.kind ?: RecordEventKind.GOAL
    val players = details.callUps.map { it.player }.sortedWith(compareBy({ it.firstName.lowercase() }, { it.lastName.lowercase() }))
    // Goals and assists only by someone who played; a card can go to anyone called up, the bench too.
    val pickable = if (kind == RecordEventKind.YELLOW || kind == RecordEventKind.RED) players else players.filter { it.id in state.scorers }
    val minute = minuteText.toIntOrNull()
    val ready = minute != null && minute in 0..150 && (!draft.needsPlayer() || draft.playerId != null)
    val us = details.team.name
    val them = details.opponent.shortName ?: details.opponent.name

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding.Horizontal).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(if (initial.key.isEmpty()) Res.string.title_add_event else Res.string.title_edit_event),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon
            )
            SegmentedFilter(
                options = RecordEventKind.entries,
                selected = kind,
                label = { stringResource(it.labelRes()) },
                onSelect = { k ->
                    draft = draft.copy(type = k.type, isOpponent = draft.isOpponent && k.hasSide, assistPlayerId = draft.assistPlayerId.takeIf { k == RecordEventKind.GOAL && draft.playerId != null })
                },
                shorten = false
            )
            if (kind.hasSide) {
                // Whose goal (an own goal: whose player put it in their own net).
                SegmentedFilter(
                    options = listOf(false, true),
                    selected = draft.isOpponent,
                    label = { if (it) them else us },
                    onSelect = { opp -> draft = draft.copy(isOpponent = opp, playerId = draft.playerId.takeIf { !opp }, assistPlayerId = null) },
                    shorten = false
                )
            }
            if (!draft.isOpponent) {
                val goal = kind == RecordEventKind.GOAL
                // Our goal can have no scorer (not known), as on the live console: "No scorer" first.
                val noScorer = stringResource(Res.string.action_no_scorer)
                PickerField(
                    label = stringResource(Res.string.label_player_section),
                    value = players.firstOrNull { it.id == draft.playerId }?.let { "${it.firstName} ${it.lastName}" } ?: noScorer.takeIf { goal },
                    options = (if (goal) listOf("" to noScorer) else emptyList()) + pickable.map { it.id to "${it.firstName} ${it.lastName}" },
                    onPick = { picked ->
                        val id = picked.ifEmpty { null }
                        draft = draft.copy(playerId = id, assistPlayerId = draft.assistPlayerId.takeIf { id != null && it != id })
                    }
                )
                // An assist goes with a known scorer.
                if (goal && draft.playerId != null) {
                    PickerField(
                        label = stringResource(Res.string.label_assist),
                        value = players.firstOrNull { it.id == draft.assistPlayerId }?.let { "${it.firstName} ${it.lastName}" } ?: stringResource(Res.string.action_no_assist),
                        options = listOf("" to stringResource(Res.string.action_no_assist)) +
                            pickable.filter { it.id != draft.playerId }.map { it.id to "${it.firstName} ${it.lastName}" },
                        onPick = { id -> draft = draft.copy(assistPlayerId = id.ifEmpty { null }) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = minuteText,
                    onValueChange = { text ->
                        minuteText = text.filter(Char::isDigit).take(3)
                        val typed = minuteText.toIntOrNull()
                        if (!periodPicked && typed != null) {
                            draft = draft.copy(period = periodForMinute(typed, state.halfLength, details.match.halves))
                        }
                    },
                    label = { Text(stringResource(Res.string.label_minute)) },
                    suffix = { Text("'") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(110.dp),
                    shape = FieldShape,
                    colors = fieldColors()
                )
                if (details.match.halves == 2) {
                    SegmentedFilter(
                        options = listOf(1, 2),
                        selected = draft.period,
                        label = { stringResource(if (it == 2) Res.string.label_second_half else Res.string.label_first_half) },
                        onSelect = { periodPicked = true; draft = draft.copy(period = it) },
                        modifier = Modifier.weight(1f),
                        shorten = false
                    )
                }
            }
            // The app's main button (green, white text), as Save under the editor.
            PrimaryLoadingButton(
                text = Res.string.btn_done,
                submittingText = Res.string.btn_done,
                isLoading = false,
                enabled = ready,
                onClick = { onSave(draft.copy(minute = minute ?: 0)) }
            )
            if (onDelete != null) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = FieldShape,
                    border = BorderStroke(1.dp, LossRed)
                ) { Text(stringResource(Res.string.btn_delete_event), color = LossRed, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private fun RecordEventKind.labelRes(): StringResource = when (this) {
    RecordEventKind.GOAL -> Res.string.label_event_type_goal
    RecordEventKind.OWN_GOAL -> Res.string.label_event_type_own_goal
    RecordEventKind.YELLOW -> Res.string.lm_action_yellow
    RecordEventKind.RED -> Res.string.lm_action_red
}

/** A labelled field that opens a list to pick from; [options] are (id, label). */
@Composable
private fun PickerField(label: String, value: String?, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DeepCarbon.copy(alpha = 0.5f))
        Spacer(Modifier.height(4.dp))
        Box {
            Surface(
                onClick = { open = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = FieldShape,
                color = Color.White,
                border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.3f))
            ) {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        value ?: stringResource(Res.string.label_choose_player),
                        Modifier.weight(1f),
                        color = if (value != null) DeepCarbon else DeepCarbon.copy(alpha = 0.4f),
                        fontWeight = if (value != null) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text("▾", color = DeepCarbon.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                }
            }
            DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.background(Color.White)) {
                options.forEach { (id, text) ->
                    DropdownMenuItem(text = { Text(text, color = DeepCarbon) }, onClick = { open = false; onPick(id) })
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
    focusedTextColor = DeepCarbon, unfocusedTextColor = DeepCarbon,
    focusedBorderColor = PitchGreen, unfocusedBorderColor = CarbonGray.copy(alpha = 0.3f),
    focusedLabelColor = PitchGreen, unfocusedLabelColor = CarbonGray
)

// ---- Previews ----

@Composable
private fun RecordPreviewFrame(afterGame: Boolean) {
    SquadifyTheme {
        val finished = previewFinishedDetails(canSeeRecord = true).let { it.copy(canManageLiveMatch = true) }
        val details = if (afterGame) finished.copy(match = finished.match.copy(status = MatchStatus.SCHEDULED, halfLengthMinutes = null), playerStats = emptyList()) else finished
        val events = if (afterGame) draftEvents(finished.events).filter { it.type == EventType.GOAL } else draftEvents(details.events)
        val minutes = details.callUps.mapIndexed { i, it -> it.player.id to if (i < 7) "60" else if (i < 9) "20" else "" }.toMap()
        MatchRecordContent(
            state = MatchRecordUiState.Idle(details, events, halfLength = 30, minutes = minutes, dirty = true),
            onNavigateBack = {}, onRetry = {}, onSaveEvent = {}, onDeleteEvent = {}, onHalfLength = {}, onMinutes = { _, _ -> }, onFullMatch = {}, onSort = {}, onSave = {}
        )
    }
}

@Preview
@Composable
private fun MatchRecordCorrectionPreview() = RecordPreviewFrame(afterGame = false)

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun MatchRecordCorrectionLandscapePreview() = RecordPreviewFrame(afterGame = false)

@Preview
@Composable
private fun MatchRecordEnterResultPreview() = RecordPreviewFrame(afterGame = true)

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun MatchRecordEnterResultLandscapePreview() = RecordPreviewFrame(afterGame = true)
