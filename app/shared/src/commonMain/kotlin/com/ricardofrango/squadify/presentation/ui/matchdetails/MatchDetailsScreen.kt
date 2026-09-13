package com.ricardofrango.squadify.presentation.ui.matchdetails

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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.*
import com.ricardofrango.squadify.presentation.ui.components.DatePickerSheet
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.components.TimePickerSheet
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import androidx.compose.ui.tooling.preview.Preview
import squadify.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Instant

private val CardShape = RoundedCornerShape(16.dp)
private val LossRed = Color(0xFFC0392B)

private val DayMonthYear = LocalDate.Format { day(); char(' '); monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); year() }
private val WeekdayDayMonth = LocalDate.Format {
    dayOfWeek(kotlinx.datetime.format.DayOfWeekNames.ENGLISH_ABBREVIATED); char(' '); day(); char(' '); monthName(MonthNames.ENGLISH_ABBREVIATED)
}

/**
 * The hub for one match (design 15A). Manager view: manage the call-up (tap a chip to cycle a
 * player's status, ＋ a pool player), edit the match's own data, and start it. Non-managers see the
 * same content read-only. The live match screen is separate -- this screen only hands off to it.
 */
@Composable
fun MatchDetailsScreen(
    matchId: String,
    onNavigateBack: () -> Unit,
    onEnterLiveMatch: (matchId: String) -> Unit,
    onRedirectToLiveMatch: (matchId: String) -> Unit,
    refreshOnReturn: Boolean = false,
    viewModel: MatchDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(matchId) { viewModel.load(matchId) }
    LaunchedEffect(refreshOnReturn) { if (refreshOnReturn) viewModel.load(matchId, forceReload = true) }
    LaunchedEffect(viewModel.navEvent) {
        viewModel.navEvent.collect { event ->
            when (event) {
                is MatchDetailsNavEvent.EnterLiveMatch -> onEnterLiveMatch(event.matchId)
                is MatchDetailsNavEvent.RedirectToLiveMatch -> onRedirectToLiveMatch(event.matchId)
            }
        }
    }

    MatchDetailsContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRetry = { viewModel.load(matchId, forceReload = true) },
        onSortSelected = viewModel::onSortSelected,
        onCycleStatus = viewModel::onCycleCallUpStatus,
        onAddToCallUp = viewModel::onAddToCallUp,
        onEditMatch = viewModel::onEditMatch,
        onEnterLiveMatch = viewModel::onEnterLiveMatch
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchDetailsContent(
    uiState: MatchDetailsUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onSortSelected: (CallUpSort) -> Unit,
    onCycleStatus: (MatchCallUpWithPlayer) -> Unit,
    onAddToCallUp: (Player) -> Unit,
    onEditMatch: (UpdateMatchRequest) -> Unit,
    onEnterLiveMatch: () -> Unit
) {
    val details = (uiState as? MatchDetailsUiState.Idle)?.details
    Scaffold(
        containerColor = Color.White,
        topBar = {
            Column(Modifier.background(CarbonGray)) {
                FMTopBar(
                    title = details?.let { "${it.team.name} vs ${it.opponent.shortName ?: it.opponent.name}" }
                        ?: stringResource(Res.string.title_match),
                    subtitle = details?.let { "${it.ageGroup.name} · ${it.season.name}" },
                    onNavigateBack = onNavigateBack
                )
                if (details != null) StatusBand(details)
            }
        },
        bottomBar = {
            if (details != null && details.canManage) {
                BottomActionBar(
                    state = uiState as MatchDetailsUiState.Idle,
                    onEnterLiveMatch = onEnterLiveMatch
                )
            }
        }
    ) { paddingValues ->
        val boxModifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.White)
        when (uiState) {
            is MatchDetailsUiState.Loading ->
                Box(boxModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PitchGreen) }

            is MatchDetailsUiState.Error ->
                Column(boxModifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(Res.string.btn_retry)) }
                }

            is MatchDetailsUiState.Idle -> MatchBody(
                state = uiState,
                modifier = boxModifier,
                onSortSelected = onSortSelected,
                onCycleStatus = onCycleStatus,
                onAddToCallUp = onAddToCallUp,
                onEditMatch = onEditMatch
            )
        }
    }
}

// ---- Header ----

@Composable
private fun StatusBand(details: MatchDetails) {
    val match = details.match
    val dt = match.dateTime.toLocalDateTime(TimeZone.currentSystemDefault())
    val counts = callUpCounts(details)
    Column(Modifier.background(CarbonGray).padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${dt.date.format(WeekdayDayMonth)} · ${timeLabel(match.dateTime)}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(Modifier.width(8.dp))
            if (match.status == MatchStatus.LIVE || match.status == MatchStatus.HALF_TIME) {
                Pill(stringResource(Res.string.matches_live), LossRed, Color.White)
            } else if (match.status == MatchStatus.SCHEDULED) {
                Pill(relativeLabel(match.dateTime), PitchGreen.copy(alpha = 0.25f), PitchGreen)
            } else if (match.status == MatchStatus.FINISHED) {
                val (bg, fg) = resultPillColors(details.score)
                Pill(stringResource(resultLabel(details.score)), bg, fg)
            }
        }
        if (match.status == MatchStatus.FINISHED) {
            Text(
                "${details.score.ourScore} – ${details.score.opponentScore}",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = resultAccent(details.score)
            )
        }
        match.location?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                Res.string.label_callup_summary,
                counts.calledUp, counts.total, counts.confirmed, counts.out, counts.pending
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg
        )
    }
}

// ---- Body ----

@Composable
private fun MatchBody(
    state: MatchDetailsUiState.Idle,
    modifier: Modifier,
    onSortSelected: (CallUpSort) -> Unit,
    onCycleStatus: (MatchCallUpWithPlayer) -> Unit,
    onAddToCallUp: (Player) -> Unit,
    onEditMatch: (UpdateMatchRequest) -> Unit
) {
    val details = state.details
    val counts = callUpCounts(details)
    // A finished match is a record, not something to keep editing -- call-ups, kickoff, location
    // etc. all freeze once the game is over, regardless of the viewer's role.
    val canManage = details.canManage && details.match.status != MatchStatus.FINISHED
    var showEdit by remember { mutableStateOf(false) }

    val standby = details.callUps.filter { it.callUp.status == CallUpStatus.STANDBY }
    val active = details.callUps.filter { it.callUp.status != CallUpStatus.STANDBY }
    val sortedActive = remember(active, state.sort) {
        when (state.sort) {
            CallUpSort.STATUS -> active.sortedWith(
                compareBy({ statusOrder(it.callUp.status) }, { it.player.lastName.lowercase() }, { it.player.firstName.lowercase() })
            )
            CallUpSort.NAME -> active.sortedWith(compareBy({ it.player.lastName.lowercase() }, { it.player.firstName.lowercase() }))
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        state.transientError?.let {
            item { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }

        // ---- Details ----
        item {
            SectionHeader(
                title = stringResource(Res.string.label_details_section),
                trailing = if (canManage) stringResource(Res.string.label_edit) to { showEdit = true } else null
            )
        }
        item { DetailsCard(details) }

        // ---- Call-up ----
        item {
            SectionHeader(
                title = stringResource(Res.string.label_call_up),
                pill = "${counts.calledUp}/${counts.total}"
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.label_sort), style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.5f))
                SortChip(stringResource(Res.string.label_sort_status), state.sort == CallUpSort.STATUS) { onSortSelected(CallUpSort.STATUS) }
                SortChip(stringResource(Res.string.label_sort_name), state.sort == CallUpSort.NAME) { onSortSelected(CallUpSort.NAME) }
                if (canManage) {
                    Spacer(Modifier.weight(1f))
                    // TODO: reminders aren't wired yet -- kept inert per the design.
                    Text(stringResource(Res.string.label_remind), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = PitchGreen.copy(alpha = 0.5f))
                }
            }
        }
        if (canManage && counts.confirmed < RECOMMENDED_SQUAD) {
            item {
                Text(
                    stringResource(Res.string.msg_squad_hint, counts.confirmed, RECOMMENDED_SQUAD),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (details.callUps.isEmpty()) {
            item { Text(stringResource(Res.string.msg_no_callups), style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.5f)) }
        } else {
            items(sortedActive, key = { it.callUp.id }) { cu -> CallUpRow(cu, canManage, onCycleStatus) }
            if (canManage) {
                item {
                    Text(stringResource(Res.string.msg_tap_status_hint), style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.45f))
                }
            }
        }

        if (standby.isNotEmpty()) {
            item { GroupLabel(stringResource(Res.string.label_standby)) }
            items(standby, key = { it.callUp.id }) { cu -> CallUpRow(cu, canManage, onCycleStatus) }
        }

        // ---- Available ----
        if (canManage && details.poolPlayers.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(Res.string.label_available), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.label_available_from_pool, details.ageGroup.name), style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.5f))
                }
            }
            items(details.poolPlayers.sortedWith(compareBy({ it.lastName.lowercase() }, { it.firstName.lowercase() })), key = { it.id }) { p ->
                PoolRow(p, onAdd = { onAddToCallUp(p) })
            }
        }
    }

    if (showEdit) {
        EditMatchSheet(
            details = details,
            submitting = state.isSubmitting,
            onSave = { onEditMatch(it); showEdit = false },
            onDismiss = { showEdit = false }
        )
    }
}

@Composable
private fun DetailsCard(details: MatchDetails) {
    val m = details.match
    val uriHandler = LocalUriHandler.current
    Surface(modifier = Modifier.fillMaxWidth(), shape = CardShape, color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))) {
        Column(Modifier.padding(vertical = 4.dp)) {
            DetailRow(stringResource(Res.string.label_opponent_field), details.opponent.name)
            DetailRow(stringResource(Res.string.label_match_date), m.dateTime.toLocalDateTime(TimeZone.currentSystemDefault()).date.format(DayMonthYear))
            DetailRow(stringResource(Res.string.label_match_kickoff), timeLabel(m.dateTime))
            DetailRow(
                stringResource(Res.string.label_match_location),
                m.location?.takeIf { it.isNotBlank() } ?: "—",
                trailing = m.location?.takeIf { it.isNotBlank() }?.let { loc ->
                    stringResource(Res.string.label_match_directions) to {
                        uriHandler.openUri("https://www.google.com/maps/search/?api=1&query=$loc")
                    }
                }
            )
            DetailRow(stringResource(Res.string.label_match_notes), m.notes?.takeIf { it.isNotBlank() } ?: "—")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, trailing: Pair<String, () -> Unit>? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.5f), modifier = Modifier.width(84.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, modifier = Modifier.weight(1f))
        if (trailing != null) LinkText(trailing.first, trailing.second)
    }
}

@Composable
private fun CallUpRow(cu: MatchCallUpWithPlayer, canManage: Boolean, onCycle: (MatchCallUpWithPlayer) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = CardShape, color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.12f))) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                cu.player.shirtNumber?.toString() ?: "—",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PitchGreen, modifier = Modifier.width(28.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("${cu.player.firstName} ${cu.player.lastName}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis)
                cu.callUp.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            StatusPill(cu.callUp.status, onClick = if (canManage) ({ onCycle(cu) }) else null)
        }
    }
}

@Composable
private fun PoolRow(player: Player, onAdd: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = CardShape, color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.12f))) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(player.shirtNumber?.toString() ?: "—", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PitchGreen, modifier = Modifier.width(28.dp))
            Spacer(Modifier.width(8.dp))
            Text("${player.firstName} ${player.lastName}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(
                modifier = Modifier.size(28.dp).background(PitchGreen.copy(alpha = 0.15f), CircleShape).clickable(onClick = onAdd),
                contentAlignment = Alignment.Center
            ) { Text("+", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PitchGreen) }
        }
    }
}

@Composable
private fun StatusPill(status: CallUpStatus, onClick: (() -> Unit)?) {
    val (bg, fg) = statusColors(status)
    val mod = if (onClick != null) {
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    } else Modifier
    Surface(shape = RoundedCornerShape(8.dp), color = bg, modifier = mod) {
        Text(
            stringResource(statusLabel(status)),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = fg
        )
    }
}

@Composable
private fun SectionHeader(title: String, pill: String? = null, trailing: Pair<String, () -> Unit>? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
        if (pill != null) {
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = CarbonGray.copy(alpha = 0.12f)) {
                Text(pill, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon.copy(alpha = 0.55f))
            }
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null) LinkText(trailing.first, trailing.second)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DeepCarbon.copy(alpha = 0.5f), modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) DeepCarbon else CarbonGray.copy(alpha = 0.08f),
        border = if (selected) null else BorderStroke(1.dp, CarbonGray.copy(alpha = 0.2f))
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = if (selected) Color.White else DeepCarbon)
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        color = PitchGreen,
        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    )
}

@Composable
private fun BottomActionBar(state: MatchDetailsUiState.Idle, onEnterLiveMatch: () -> Unit) {
    val status = state.details.match.status
    if (status != MatchStatus.SCHEDULED && status != MatchStatus.LIVE && status != MatchStatus.HALF_TIME) return
    val scheduled = status == MatchStatus.SCHEDULED
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // The live match screen owns starting the game / substitutions / events -- this button
            // only opens it.
            val label = if (scheduled) Res.string.btn_start_match else Res.string.btn_enter_live_match
            PrimaryLoadingButton(
                text = label,
                submittingText = label,
                isLoading = false,
                onClick = onEnterLiveMatch
            )
            if (scheduled) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(Res.string.msg_kickoff_countdown, relativeLabel(state.details.match.dateTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMatchSheet(details: MatchDetails, submitting: Boolean, onSave: (UpdateMatchRequest) -> Unit, onDismiss: () -> Unit) {
    val tz = TimeZone.currentSystemDefault()
    val initial = details.match.dateTime.toLocalDateTime(tz)
    var date by remember { mutableStateOf(initial.date) }
    var time by remember { mutableStateOf(LocalTime(initial.hour, initial.minute)) }
    var location by remember { mutableStateOf(details.match.location.orEmpty()) }
    var notes by remember { mutableStateOf(details.match.notes.orEmpty()) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.title_edit_match), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
            ValueField(stringResource(Res.string.label_match_date), date.format(DayMonthYear)) { showDate = true }
            ValueField(stringResource(Res.string.label_match_kickoff), timeOf(time)) { showTime = true }
            OutlinedTextField(
                value = location, onValueChange = { location = it },
                label = { Text(stringResource(Res.string.label_match_location)) },
                placeholder = { Text(stringResource(Res.string.label_match_location_placeholder)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = CardShape, colors = editFieldColors()
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text(stringResource(Res.string.label_match_notes)) },
                placeholder = { Text(stringResource(Res.string.label_match_notes_placeholder)) },
                minLines = 2, modifier = Modifier.fillMaxWidth(), shape = CardShape, colors = editFieldColors()
            )
            Button(
                onClick = {
                    onSave(
                        UpdateMatchRequest(
                            dateTime = date.atTime(time).toInstant(tz),
                            location = location.trim().ifBlank { null },
                            notes = notes.trim().ifBlank { null }
                        )
                    )
                },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White)
            ) { Text(stringResource(Res.string.btn_save)) }
        }
    }

    if (showDate) {
        DatePickerSheet(
            title = Res.string.label_select_match_date,
            initialDate = date,
            onConfirm = { showDate = false; date = it },
            onDismiss = { showDate = false }
        )
    }
    if (showTime) {
        TimePickerSheet(
            title = Res.string.label_select_kickoff_time,
            initialTime = time,
            onConfirm = { showTime = false; time = it },
            onDismiss = { showTime = false }
        )
    }
}

@Composable
private fun ValueField(label: String, value: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = CardShape, color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.3f))) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = DeepCarbon.copy(alpha = 0.5f))
                Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
            }
            Text("▾", color = DeepCarbon.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DeepCarbon,
    unfocusedTextColor = DeepCarbon,
    focusedBorderColor = PitchGreen,
    unfocusedBorderColor = CarbonGray.copy(alpha = 0.3f),
    focusedLabelColor = PitchGreen,
    unfocusedLabelColor = CarbonGray
)

// ---- Helpers ----

private const val RECOMMENDED_SQUAD = 11

private data class CallUpCounts(val calledUp: Int, val total: Int, val confirmed: Int, val out: Int, val pending: Int)

private fun callUpCounts(details: MatchDetails): CallUpCounts {
    val active = details.callUps.filter { it.callUp.status != CallUpStatus.STANDBY }
    return CallUpCounts(
        calledUp = active.size,
        total = details.callUps.size + details.poolPlayers.size,
        confirmed = active.count { it.callUp.status == CallUpStatus.CONFIRMED },
        out = active.count { it.callUp.status == CallUpStatus.DECLINED || it.callUp.status == CallUpStatus.NOT_AVAILABLE },
        pending = active.count { it.callUp.status == CallUpStatus.CALLED_UP }
    )
}

private fun statusOrder(s: CallUpStatus): Int = when (s) {
    CallUpStatus.CONFIRMED -> 0
    CallUpStatus.CALLED_UP -> 1
    CallUpStatus.DECLINED -> 2
    CallUpStatus.NOT_AVAILABLE -> 3
    CallUpStatus.STANDBY -> 4
}

private fun statusLabel(s: CallUpStatus): StringResource = when (s) {
    CallUpStatus.CALLED_UP -> Res.string.label_callup_status_called_up
    CallUpStatus.CONFIRMED -> Res.string.label_callup_status_confirmed
    CallUpStatus.DECLINED -> Res.string.label_callup_status_declined
    CallUpStatus.NOT_AVAILABLE -> Res.string.label_callup_status_not_available
    CallUpStatus.STANDBY -> Res.string.label_callup_status_standby
}

private fun statusColors(s: CallUpStatus): Pair<Color, Color> = when (s) {
    CallUpStatus.CONFIRMED -> PitchGreen.copy(alpha = 0.15f) to PitchGreen
    CallUpStatus.DECLINED, CallUpStatus.NOT_AVAILABLE -> LossRed.copy(alpha = 0.12f) to LossRed
    CallUpStatus.CALLED_UP, CallUpStatus.STANDBY -> CarbonGray.copy(alpha = 0.14f) to DeepCarbon.copy(alpha = 0.6f)
}

private fun resultAccent(score: MatchScore): Color = when {
    score.ourScore > score.opponentScore -> PitchGreen
    score.ourScore < score.opponentScore -> LossRed
    else -> Color.White.copy(alpha = 0.75f)
}

private fun resultPillColors(score: MatchScore): Pair<Color, Color> = when {
    score.ourScore > score.opponentScore -> PitchGreen.copy(alpha = 0.25f) to PitchGreen
    score.ourScore < score.opponentScore -> LossRed.copy(alpha = 0.25f) to LossRed
    else -> Color.White.copy(alpha = 0.15f) to Color.White.copy(alpha = 0.75f)
}

private fun resultLabel(score: MatchScore): StringResource = when {
    score.ourScore > score.opponentScore -> Res.string.label_result_win
    score.ourScore < score.opponentScore -> Res.string.label_result_loss
    else -> Res.string.label_result_draw
}

private fun timeLabel(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
}

private fun timeOf(t: LocalTime): String = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"

@Composable
private fun relativeLabel(instant: Instant): String {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.todayIn(tz)
    val target = instant.toLocalDateTime(tz).date
    val days = (target.toEpochDays() - today.toEpochDays()).toInt()
    return when {
        days <= 0 -> stringResource(Res.string.label_relative_today)
        days == 1 -> stringResource(Res.string.label_relative_tomorrow)
        days < 7 -> stringResource(Res.string.label_relative_in_days, days)
        else -> stringResource(Res.string.label_relative_in_weeks, days / 7)
    }
}

// ---- Previews ----

private fun sampleDetails(status: MatchStatus, canManage: Boolean, score: MatchScore = MatchScore("m1", 0, 0)) = MatchDetails(
    match = Match(
        id = "m1", clubId = "c1", teamId = "t1", opponentId = "o1",
        location = "Municipal Stadium", dateTime = Instant.fromEpochMilliseconds(0), status = status
    ),
    opponent = Opponent(id = "o1", clubId = "c1", name = "Sporting Youth", shortName = "Sporting"),
    team = Team(id = "t1", clubId = "c1", ageGroupId = "ag1", seasonId = "s1", name = "Sub-15 A", active = true),
    ageGroup = AgeGroup(id = "ag1", clubId = "c1", name = "Sub-15", seasonId = "s1", birthYear = 2011),
    season = Season(
        id = "s1", clubId = "c1", name = "26/27", type = SeasonType.EUROPEAN_YEAR,
        startDate = LocalDate(2026, 7, 1), endDate = LocalDate(2027, 6, 30), active = true
    ),
    callUps = listOf(
        MatchCallUpWithPlayer(
            callUp = MatchCallUp(id = "cu1", matchId = "m1", playerId = "p1", status = CallUpStatus.CONFIRMED),
            player = Player(id = "p1", clubId = "c1", firstName = "Tomás", lastName = "Bragança", birthDate = LocalDate(2011, 3, 12), shirtNumber = 10, active = true)
        )
    ),
    poolPlayers = listOf(
        Player(id = "p2", clubId = "c1", firstName = "Xavier", lastName = "Boaventura", birthDate = LocalDate(2011, 8, 4), shirtNumber = 7, active = true)
    ),
    canManage = canManage,
    score = score
)

@Preview
@Composable
private fun MatchDetailsFinishedWinPreview() {
    SquadifyTheme {
        MatchDetailsContent(
            uiState = MatchDetailsUiState.Idle(sampleDetails(MatchStatus.FINISHED, canManage = true, score = MatchScore("m1", 3, 1))),
            onNavigateBack = {}, onRetry = {}, onSortSelected = {}, onCycleStatus = {}, onAddToCallUp = {}, onEditMatch = {}, onEnterLiveMatch = {}
        )
    }
}

@Preview
@Composable
private fun MatchDetailsScheduledCanManagePreview() {
    SquadifyTheme {
        MatchDetailsContent(
            uiState = MatchDetailsUiState.Idle(sampleDetails(MatchStatus.SCHEDULED, canManage = true)),
            onNavigateBack = {}, onRetry = {}, onSortSelected = {}, onCycleStatus = {}, onAddToCallUp = {}, onEditMatch = {}, onEnterLiveMatch = {}
        )
    }
}

@Preview
@Composable
private fun MatchDetailsLoadingPreview() {
    SquadifyTheme {
        MatchDetailsContent(
            uiState = MatchDetailsUiState.Loading,
            onNavigateBack = {}, onRetry = {}, onSortSelected = {}, onCycleStatus = {}, onAddToCallUp = {}, onEditMatch = {}, onEnterLiveMatch = {}
        )
    }
}
