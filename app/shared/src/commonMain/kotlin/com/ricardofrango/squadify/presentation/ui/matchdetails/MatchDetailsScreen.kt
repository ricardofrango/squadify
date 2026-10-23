package com.ricardofrango.squadify.presentation.ui.matchdetails

import com.ricardofrango.squadify.presentation.ui.components.venueLocation
import com.ricardofrango.squadify.presentation.ui.components.VenueSelector
import com.ricardofrango.squadify.presentation.ui.theme.IsClubThemed
import com.ricardofrango.squadify.presentation.ui.theme.ChromeAccent
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.ricardofrango.squadify.presentation.ui.components.ClubLogo
import com.ricardofrango.squadify.presentation.ui.components.ConfirmDialog
import com.ricardofrango.squadify.presentation.ui.components.MatchTypeChip
import com.ricardofrango.squadify.presentation.ui.components.HalvesSelector
import com.ricardofrango.squadify.presentation.ui.components.MatchTypeSelector
import com.ricardofrango.squadify.presentation.ui.components.iconRes
import com.ricardofrango.squadify.presentation.ui.components.DatePickerSheet
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.components.TimePickerSheet
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SuccessGreen
import com.ricardofrango.squadify.presentation.ui.theme.WhiteSmoke
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import androidx.compose.ui.tooling.preview.Preview
import com.ricardofrango.squadify.presentation.ui.components.labelRes
import com.ricardofrango.squadify.presentation.ui.components.PlayerBadge
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
import org.jetbrains.compose.resources.painterResource
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
 * The hub for one match (design 15A). Manager view: manage the call-up (＋ a pool player -- being
 * called up is binary, no per-player status; see CallUpStatus), edit the match's own data, and start
 * it. Non-managers see the same content read-only. The live match screen is separate -- this screen
 * only hands off to it.
 */
@Composable
fun MatchDetailsScreen(
    matchId: String,
    onNavigateBack: () -> Unit,
    onEnterLiveMatch: (matchId: String) -> Unit,
    onRedirectToLiveMatch: (matchId: String) -> Unit,
    onOpenReport: (matchId: String, edit: Boolean) -> Unit,
    refreshOnReturn: Boolean = false,
    viewModel: MatchDetailsViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val uiState = viewModel.state.collectSettledState()

        LaunchedEffect(matchId) { viewModel.load(matchId) }
        LaunchedEffect(refreshOnReturn) { if (refreshOnReturn) viewModel.load(matchId, forceReload = true) }
        LaunchedEffect(viewModel.navEvent) {
            viewModel.navEvent.collect { event ->
                when (event) {
                    is MatchDetailsNavEvent.EnterLiveMatch -> onEnterLiveMatch(event.matchId)
                    is MatchDetailsNavEvent.RedirectToLiveMatch -> onRedirectToLiveMatch(event.matchId)
                    is MatchDetailsNavEvent.MatchDeleted -> onNavigateBack()
                }
            }
        }

        MatchDetailsContent(
            uiState = uiState,
            onNavigateBack = onNavigateBack,
            onRetry = { viewModel.load(matchId, forceReload = true) },
            onSortSelected = viewModel::onSortSelected,
            onAddToCallUp = viewModel::onAddToCallUp,
            onRemoveFromCallUp = viewModel::onRemoveFromCallUp,
            onSaveCallUp = viewModel::onSaveCallUp,
            onEditMatch = viewModel::onEditMatch,
            onDeleteMatch = viewModel::onDeleteMatch,
            onEnterLiveMatch = viewModel::onEnterLiveMatch,
            onOpenReport = { edit -> onOpenReport(matchId, edit) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchDetailsContent(
    uiState: MatchDetailsUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onSortSelected: (CallUpSort) -> Unit,
    onAddToCallUp: (Player) -> Unit,
    onRemoveFromCallUp: (Player) -> Unit,
    onEditMatch: (UpdateMatchRequest) -> Unit,
    onDeleteMatch: () -> Unit,
    onEnterLiveMatch: () -> Unit,
    onSaveCallUp: () -> Unit = {},
    onOpenReport: (edit: Boolean) -> Unit
) {
    val details = (uiState as? MatchDetailsUiState.Idle)?.details
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Leaving with a call-up that hasn't been saved asks first (the top bar's back and the system's).
    val callUpUnsaved = (uiState as? MatchDetailsUiState.Idle)?.hasUnsavedCallUp == true
    var showDiscard by remember { mutableStateOf(false) }
    val requestBack = { if (callUpUnsaved) showDiscard = true else onNavigateBack() }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = callUpUnsaved,
        onBackCompleted = { showDiscard = true }
    )
    Scaffold(
        containerColor = Color.White,
        topBar = {
            Column(Modifier.background(ClubChrome)) {
                // The teams and the score are the band below; the bar says when and what kind of game.
                SquadifyTopBar(
                    title = details?.let { whenLabel(it.match.dateTime) } ?: stringResource(Res.string.title_match),
                    subtitle = details?.let { matchSubtitle(it) },
                    onNavigateBack = requestBack,
                    actions = {
                        // Delete is the menu's only item, and a played (FINISHED) match can't be deleted.
                        if (details != null && details.canManageMatch && details.match.status != MatchStatus.FINISHED) {
                            var menuOpen by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_more),
                                        contentDescription = stringResource(Res.string.label_more_options),
                                        tint = Color.White
                                    )
                                }
                                DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.background(Color.White)) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.label_delete_match), color = LossRed) },
                                        onClick = { menuOpen = false; showDeleteConfirm = true }
                                    )
                                }
                            }
                        }
                    }
                )
                if (details != null) StatusBand(details)
            }
        },
        bottomBar = {
            // Save call-up / Start match / Enter live match, by permission and call-up state (callUpView).
            val idle = uiState as? MatchDetailsUiState.Idle
            val button = idle?.callUpView()?.button ?: MatchDetailsButton.NONE
            if (idle != null && button != MatchDetailsButton.NONE) {
                BottomActionBar(state = idle, button = button, onSaveCallUp = onSaveCallUp, onEnterLiveMatch = onEnterLiveMatch)
            }
        }
    ) { paddingValues ->
        // A finished match's body is WhiteSmoke so its white bordered cards (the report card first) read as cards (design 23).
        val bodyColor = if (details?.match?.status == MatchStatus.FINISHED) WhiteSmoke else Color.White
        val boxModifier = Modifier.fillMaxSize().padding(paddingValues).background(bodyColor)
        when (uiState) {
            is MatchDetailsUiState.Loading ->
                Box(boxModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PitchGreen) }

            is MatchDetailsUiState.Error ->
                Column(boxModifier.screenContentPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(Res.string.btn_retry)) }
                }

            is MatchDetailsUiState.Idle -> MatchBody(
                state = uiState,
                modifier = boxModifier,
                onSortSelected = onSortSelected,
                onAddToCallUp = onAddToCallUp,
                onRemoveFromCallUp = onRemoveFromCallUp,
                onEditMatch = onEditMatch,
                onOpenReport = onOpenReport
            )
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            containerColor = Color.White,
            titleContentColor = DeepCarbon,
            textContentColor = DeepCarbon.copy(alpha = 0.75f),
            title = { Text(stringResource(Res.string.club_discard_title)) },
            text = { Text(stringResource(Res.string.msg_callup_discard)) },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; onNavigateBack() }) {
                    Text(stringResource(Res.string.club_discard_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text(stringResource(Res.string.btn_keep_editing), color = CarbonGray) } }
        )
    }
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(Res.string.msg_confirm_delete_match_title),
            body = stringResource(Res.string.msg_confirm_delete_match_body),
            onConfirm = { showDeleteConfirm = false; onDeleteMatch() },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

// ---- Header ----

@OptIn(ExperimentalLayoutApi::class)
/**
 * The scoreboard line under the top bar, as football shows it: "[logo] Team 2 – 1 Opponent [logo]".
 * Each logo only when it's set, the score only once there is one (live or finished) -- before that,
 * "vs". Small enough for both names to fit; a long one is cut with "…".
 */
@Composable
private fun StatusBand(details: MatchDetails) {
    val status = details.match.status
    val hasScore = status == MatchStatus.FINISHED || status == MatchStatus.LIVE || status == MatchStatus.HALF_TIME
    val nameStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
    val nameColor = Color.White.copy(alpha = if (status == MatchStatus.CANCELLED) 0.55f else 1f)
    // Home on the left, as football reads a score; our club at home or on neutral ground.
    val us = BandSide(details.team.name, details.clubLogoUrl, details.score.ourScore)
    val them = BandSide(details.opponent.shortName ?: details.opponent.name, details.opponent.logoUrl, details.score.opponentScore)
    val (left, right) = if (details.match.venue.ourSideFirst()) us to them else them to us
    Row(
        Modifier.fillMaxWidth().background(ClubChrome).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            left.logoUrl?.let { BandCrest(it, left.name); Spacer(Modifier.width(8.dp)) }
            Text(left.name, style = nameStyle, color = nameColor, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
        }
        Text(
            if (hasScore) "${left.score} – ${right.score}" else "vs",
            style = if (hasScore) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black) else MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (hasScore) Color.White else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(right.name, style = nameStyle, color = nameColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            right.logoUrl?.let { Spacer(Modifier.width(8.dp)); BandCrest(it, right.name) }
        }
    }
}

private data class BandSide(val name: String, val logoUrl: String?, val score: Int)

/** A side's logo on the band, on a white disc so any crest reads on the bar's colour. */
@Composable
private fun BandCrest(logoUrl: String, name: String) =
    ClubLogo(logoUrl, name.take(2).uppercase(), size = 28.dp, background = Color.White, initialsColor = DeepCarbon)

/** "Sat 24 Oct · 10:30": the top bar's title. */
@Composable
private fun whenLabel(instant: Instant): String =
    "${instant.toLocalDateTime(TimeZone.currentSystemDefault()).date.format(WeekdayDayMonth)} · ${timeLabel(instant)}"

/** The type, then what's notable: "Championship · in 3 days", "Friendly · Cancelled", "… · 1 half". */
@Composable
private fun matchSubtitle(details: MatchDetails): String {
    val match = details.match
    return listOfNotNull(
        match.type?.let { stringResource(it.labelRes()) },
        when (match.status) {
            MatchStatus.SCHEDULED -> relativeLabel(match.dateTime)
            MatchStatus.CANCELLED -> stringResource(Res.string.status_cancelled)
            MatchStatus.LIVE, MatchStatus.HALF_TIME -> stringResource(Res.string.matches_live)
            else -> null
        },
        // Two halves is the norm and goes unsaid; a single-period match says so.
        if (match.halves == 1) stringResource(Res.string.halves_one) else null
    ).joinToString(" · ").ifEmpty { "${details.ageGroup.name} · ${details.season.name}" }
}

// ---- Body ----

@Composable
private fun MatchBody(
    state: MatchDetailsUiState.Idle,
    modifier: Modifier,
    onSortSelected: (CallUpSort) -> Unit,
    onAddToCallUp: (Player) -> Unit,
    onRemoveFromCallUp: (Player) -> Unit,
    onEditMatch: (UpdateMatchRequest) -> Unit,
    onOpenReport: (edit: Boolean) -> Unit
) {
    val details = state.details
    val view = state.callUpView()
    // A finished match is a record, not something to keep editing -- call-ups, kickoff, location
    // etc. all freeze once the game is over, regardless of the viewer's role.
    val notFinished = details.match.status != MatchStatus.FINISHED
    // Cancelled is closed too: neither can be edited, the type included (the server enforces the same).
    val canEditMatch = details.canManageMatch && notFinished && details.match.status != MatchStatus.CANCELLED
    var showEdit by remember { mutableStateOf(false) }

    // The Position / Name chips order both lists -- the call-up and the Available pool below it.
    val playerOrder = playerComparator(state.sort)
    val editing = view.section == CallUpSection.EDITOR
    // The editor lists the draft call-up; everyone else sees the saved one.
    val calledUp = (if (editing) state.selectedPlayers else details.callUps.map { it.player }).sortedWith(playerOrder)
    val sortedPool = state.availablePlayers.sortedWith(playerOrder)
    val notesByPlayer = details.callUps.associate { it.player.id to it.callUp.notes }

    // Landscape with a report card: the card gets its own fixed 320dp left column and stays in view
    // while the rest scrolls beside it -- it's the pending task on a finished match (23c).
    BoxWithConstraints(modifier) {
        val sideCard = details.report != null && maxWidth > maxHeight && maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (sideCard) {
                Box(Modifier.width(344.dp).padding(start = ScreenPadding.Horizontal, top = ScreenPadding.Top)) {
                    MatchReportCard(details.report!!, onOpenReport)
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = ScreenPadding.Values,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                state.transientError?.let {
                    item { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }

                // ---- Match report: the first card on a finished match, for staff only (design 23) ----
                if (!sideCard) details.report?.let { report ->
                    item { MatchReportCard(report, onOpenReport) }
                }

                // ---- Details ----
                item {
                    SectionHeader(
                        title = stringResource(Res.string.label_details_section),
                        trailing = if (canEditMatch) stringResource(Res.string.label_edit) to { showEdit = true } else null,
                        // First in the list unless an error or the report card comes before it.
                        first = state.transientError == null && (sideCard || details.report == null)
                    )
                }
                item { DetailsCard(details, onSetType = if (canEditMatch) { { showEdit = true } } else null) }

                // ---- Call-up: by who's looking (callUpView) ----
                when (view.section) {
                    CallUpSection.HIDDEN -> Unit
                    CallUpSection.NOT_READY -> item {
                        Text(stringResource(Res.string.msg_callup_not_ready), style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
                    }
                    CallUpSection.EDITOR, CallUpSection.READ_ONLY -> {
                        item {
                            SectionHeader(
                                title = stringResource(Res.string.label_call_up),
                                // Once the game is played the count says nothing more; it's dropped.
                                pill = when {
                                    details.match.status == MatchStatus.FINISHED -> null
                                    editing -> "${calledUp.size}/${calledUp.size + sortedPool.size}"
                                    else -> calledUp.size.toString()
                                }
                            )
                        }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(Res.string.label_sort), style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.5f))
                                SortChip(stringResource(Res.string.label_position_field), state.sort == CallUpSort.POSITION) { onSortSelected(CallUpSort.POSITION) }
                                SortChip(stringResource(Res.string.label_sort_name), state.sort == CallUpSort.NAME) { onSortSelected(CallUpSort.NAME) }
                            }
                        }
                        if (editing && calledUp.size < RECOMMENDED_SQUAD) {
                            item {
                                Text(
                                    stringResource(Res.string.msg_squad_hint, calledUp.size, RECOMMENDED_SQUAD),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (calledUp.isEmpty()) {
                            item { Text(stringResource(Res.string.msg_no_callups), style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.5f)) }
                        } else {
                            items(calledUp, key = { "called-${it.id}" }) { player ->
                                CallUpRow(player, notesByPlayer[player.id], onRemove = if (editing && !state.isSubmitting) { { onRemoveFromCallUp(player) } } else null)
                            }
                        }

                        // ---- Available ----
                        if (editing && sortedPool.isNotEmpty()) {
                            item {
                                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 12.dp)) {
                                    Text(stringResource(Res.string.label_available), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(Res.string.label_available_from_pool, details.ageGroup.name), style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.5f))
                                }
                            }
                            items(sortedPool, key = { "pool-${it.id}" }) { p ->
                                PoolRow(p, onAdd = { if (!state.isSubmitting) onAddToCallUp(p) })
                            }
                        }
                    }
                }
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
private fun DetailsCard(details: MatchDetails, onSetType: (() -> Unit)?) {
    val m = details.match
    val uriHandler = LocalUriHandler.current
    Surface(modifier = Modifier.fillMaxWidth(), shape = CardShape, color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))) {
        Column(Modifier.padding(vertical = 4.dp)) {
            DetailRow(stringResource(Res.string.label_opponent_field), details.opponent.name)
            TypeRow(m.type, onSetType)
            // The format is the team's (how many a side it plays): 5x5, 7x7, 9x9, 11x11.
            DetailRow(stringResource(Res.string.label_match_format), details.team.teamSize?.let { stringResource(Res.string.match_format_value, it) } ?: "—")
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
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.5f), modifier = Modifier.width(84.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, modifier = Modifier.weight(1f))
        if (trailing != null) LinkText(trailing.first, trailing.second)
    }
}

/**
 * The match type as the record of it: icon + name. Older matches have none -- a manager of an editable match sees "Not set"
 * with a "Set type" link that opens the edit sheet; otherwise (read-only, finished, cancelled) just "—".
 */
@Composable
private fun TypeRow(type: MatchType?, onSetType: (() -> Unit)?) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.label_type_row), style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.5f), modifier = Modifier.width(84.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            when {
                type != null -> {
                    Icon(painterResource(type.iconRes()), contentDescription = null, tint = PitchGreen, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(type.labelRes()), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                }
                onSetType != null -> Text(stringResource(Res.string.label_not_set), style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.62f))
                else -> Text("—", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
            }
        }
        if (type == null && onSetType != null) LinkText(stringResource(Res.string.label_set_type), onSetType)
    }
}

@Composable
private fun CallUpRow(player: Player, notes: String?, onRemove: (() -> Unit)? = null) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = CardShape, color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.12f))) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayerBadge(player.photoUrl, player.shirtNumber)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${player.firstName} ${player.lastName}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis)
                player.position?.let {
                    Text(stringResource(it.labelRes()), style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.55f))
                }
                notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (onRemove != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(28.dp).background(LossRed.copy(alpha = 0.12f), CircleShape).clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) { Text("−", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = LossRed) }
            }
        }
    }
}

@Composable
private fun PoolRow(player: Player, onAdd: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = CardShape, color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.12f))) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayerBadge(player.photoUrl, player.shirtNumber)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${player.firstName} ${player.lastName}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis)
                player.position?.let {
                    Text(stringResource(it.labelRes()), style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.55f))
                }
            }
            Box(
                modifier = Modifier.size(28.dp).background(PitchGreen.copy(alpha = 0.15f), CircleShape).clickable(onClick = onAdd),
                contentAlignment = Alignment.Center
            ) { Text("+", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PitchGreen) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, pill: String? = null, trailing: Pair<String, () -> Unit>? = null, first: Boolean = false) {
    // The gap above separates a section from the one before; the list's first header doesn't need it.
    Row(modifier = Modifier.fillMaxWidth().padding(top = if (first) 0.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) (if (IsClubThemed) ClubChrome else DeepCarbon) else CarbonGray.copy(alpha = 0.08f),
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
private fun BottomActionBar(state: MatchDetailsUiState.Idle, button: MatchDetailsButton, onSaveCallUp: () -> Unit, onEnterLiveMatch: () -> Unit) {
    val scheduled = state.details.match.status == MatchStatus.SCHEDULED
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = ScreenPadding.Horizontal, vertical = 12.dp)
        ) {
            when (button) {
                MatchDetailsButton.SAVE_CALL_UP, MatchDetailsButton.SAVE_CALL_UP_DISABLED -> PrimaryLoadingButton(
                    text = Res.string.btn_save_callup,
                    submittingText = Res.string.btn_saving_callup,
                    isLoading = state.isSubmitting,
                    enabled = button == MatchDetailsButton.SAVE_CALL_UP,
                    onClick = onSaveCallUp
                )
                // The live match screen owns starting the game / substitutions / events -- this
                // button only opens it.
                MatchDetailsButton.START_MATCH, MatchDetailsButton.ENTER_LIVE_MATCH -> {
                    val label = if (button == MatchDetailsButton.START_MATCH) Res.string.btn_start_match else Res.string.btn_enter_live_match
                    PrimaryLoadingButton(text = label, submittingText = label, isLoading = false, onClick = onEnterLiveMatch)
                }
                MatchDetailsButton.NONE -> Unit
            }
            val hint = when {
                button == MatchDetailsButton.SAVE_CALL_UP_DISABLED -> stringResource(Res.string.msg_callup_min_players, state.details.minimumCallUp())
                scheduled -> stringResource(Res.string.msg_kickoff_countdown, relativeLabel(state.details.match.dateTime))
                else -> null
            }
            hint?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
    var type by remember { mutableStateOf(details.match.type) }
    var halves by remember { mutableStateOf(details.match.halves) }
    var venue by remember { mutableStateOf(details.match.venue) }
    // The number of halves drives the live console, so it's only changeable before kickoff.
    val canChangeHalves = details.match.status == MatchStatus.SCHEDULED
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
        // The fields scroll; Save stays pinned to the bottom of the sheet, so it's always reachable
        // on a short screen or with the keyboard up.
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(Res.string.title_edit_match), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
            // The type comes first, in the same vertical rows as Schedule match (design 29c). An older
            // match with no type has nothing preselected, and Save stays enabled for other edits.
            Row {
                Text(stringResource(Res.string.label_match_type).uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DeepCarbon.copy(alpha = 0.55f))
                Spacer(Modifier.width(6.dp))
                Text("*", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error)
            }
            MatchTypeSelector(selected = type, onSelected = { type = it }, showHint = false)
            if (canChangeHalves) {
                Text(stringResource(Res.string.label_halves).uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DeepCarbon.copy(alpha = 0.55f))
                HalvesSelector(halves = halves, onSelected = { halves = it })
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(Res.string.label_venue).uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DeepCarbon.copy(alpha = 0.55f))
                // Changing where it's played moves the location with it; it can still be edited after.
                VenueSelector(selected = venue, onSelected = { venue = it; location = venueLocation(it, details.clubLocation, details.opponent) })
            }
            ValueField(stringResource(Res.string.label_match_date), date.format(DayMonthYear)) { showDate = true }
            ValueField(stringResource(Res.string.label_match_kickoff), timeOf(time)) { showTime = true }
            OutlinedTextField(
                value = location, onValueChange = { location = it },
                label = { Text(stringResource(Res.string.label_match_location)) },
                placeholder = { Text(stringResource(Res.string.label_match_location_placeholder)) },
                // Next moves to Notes (multi-line, so its Enter key adds a line).
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = CardShape, colors = editFieldColors()
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text(stringResource(Res.string.label_match_notes)) },
                placeholder = { Text(stringResource(Res.string.label_match_notes_placeholder)) },
                minLines = 2, modifier = Modifier.fillMaxWidth(), shape = CardShape, colors = editFieldColors()
            )
        }
        Column(Modifier.fillMaxWidth().background(Color.White)) {
            HorizontalDivider(color = CarbonGray.copy(alpha = 0.1f))
            Button(
                onClick = {
                    onSave(
                        UpdateMatchRequest(
                            dateTime = date.atTime(time).toInstant(tz),
                            // Sent even when emptied: blank clears it (neutral ground), null would keep it.
                            location = location.trim(),
                            notes = notes.trim().ifBlank { null },
                            type = type,
                            venue = venue,
                            halves = halves.takeIf { canChangeHalves }
                        )
                    )
                },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 12.dp).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White)
            ) { Text(stringResource(Res.string.btn_save_changes), fontWeight = FontWeight.Bold) }
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

/** [PlayerPosition]'s declared order is already Goalkeeper → Defender → Midfield → Winger →
 * Striker, so its ordinal doubles as the sort rank; a player with no position sorts last. Either
 * sort falls back to last/first name as a tiebreaker (and as the sole key for [CallUpSort.NAME]). */
// Sorted first name, then last name -- matching the order the row actually displays ("Tomás
// Bragança"), so the sort reads as working rather than seemingly-random. Sorting by last name
// first would be the more familiar convention on a roster, but not when the row itself leads with
// the first name.
internal fun playerComparator(sort: CallUpSort): Comparator<Player> = when (sort) {
    CallUpSort.NAME -> compareBy({ it.firstName.lowercase() }, { it.lastName.lowercase() })
    CallUpSort.POSITION -> compareBy(
        { it.position?.ordinal ?: Int.MAX_VALUE },
        { it.firstName.lowercase() },
        { it.lastName.lowercase() }
    )
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
    ageGroup = AgeGroup(id = "ag1", clubId = "c1", name = "Sub-15", seasonId = "s1", startYear = 2011, endYear = 2011),
    season = Season(
        id = "s1", clubId = "c1", name = "26/27", type = SeasonType.EUROPEAN_YEAR,
        startDate = LocalDate(2026, 7, 1), endDate = LocalDate(2027, 6, 30), active = true
    ),
    callUps = listOf(
        MatchCallUpWithPlayer(
            callUp = MatchCallUp(id = "cu1", matchId = "m1", playerId = "p1", status = CallUpStatus.CALLED_UP),
            player = Player(id = "p1", clubId = "c1", firstName = "Tomás", lastName = "Bragança", birthDate = LocalDate(2011, 3, 12), shirtNumber = 10, active = true, position = PlayerPosition.MIDFIELD)
        ),
        MatchCallUpWithPlayer(
            callUp = MatchCallUp(id = "cu2", matchId = "m1", playerId = "p3", status = CallUpStatus.CALLED_UP),
            player = Player(id = "p3", clubId = "c1", firstName = "Ana", lastName = "Costa", birthDate = LocalDate(2011, 5, 2), shirtNumber = 1, active = true, position = PlayerPosition.GOALKEEPER)
        ),
        MatchCallUpWithPlayer(
            callUp = MatchCallUp(id = "cu3", matchId = "m1", playerId = "p4", status = CallUpStatus.CALLED_UP),
            player = Player(id = "p4", clubId = "c1", firstName = "Beatriz", lastName = "Almeida", birthDate = LocalDate(2011, 9, 18), shirtNumber = 9, active = true, position = PlayerPosition.STRIKER)
        )
    ),
    poolPlayers = listOf(
        Player(id = "p2", clubId = "c1", firstName = "Xavier", lastName = "Boaventura", birthDate = LocalDate(2011, 8, 4), shirtNumber = 7, active = true)
    ),
    canManageMatch = canManage,
    canManageCallUps = canManage,
    canManageLiveMatch = canManage,
    score = score
)

@Preview
@Composable
private fun MatchDetailsFinishedWinPreview() {
    SquadifyTheme {
        MatchDetailsContent(
            uiState = MatchDetailsUiState.Idle(sampleDetails(MatchStatus.FINISHED, canManage = true, score = MatchScore("m1", 3, 1))),
            onNavigateBack = {}, onRetry = {}, onSortSelected = {}, onAddToCallUp = {}, onRemoveFromCallUp = {}, onEditMatch = {}, onDeleteMatch = {}, onEnterLiveMatch = {}, onOpenReport = {}
        )
    }
}

@Preview
@Composable
private fun MatchDetailsScheduledCanManagePreview() {
    SquadifyTheme {
        MatchDetailsContent(
            uiState = MatchDetailsUiState.Idle(sampleDetails(MatchStatus.SCHEDULED, canManage = true)),
            onNavigateBack = {}, onRetry = {}, onSortSelected = {}, onAddToCallUp = {}, onRemoveFromCallUp = {}, onEditMatch = {}, onDeleteMatch = {}, onEnterLiveMatch = {}, onOpenReport = {}
        )
    }
}

@Preview
@Composable
private fun MatchDetailsSortedByPositionPreview() {
    SquadifyTheme {
        MatchDetailsContent(
            uiState = MatchDetailsUiState.Idle(
                details = sampleDetails(MatchStatus.SCHEDULED, canManage = true),
                sort = CallUpSort.POSITION
            ),
            onNavigateBack = {}, onRetry = {}, onSortSelected = {}, onAddToCallUp = {}, onRemoveFromCallUp = {}, onEditMatch = {}, onDeleteMatch = {}, onEnterLiveMatch = {}, onOpenReport = {}
        )
    }
}

@Preview
@Composable
private fun MatchDetailsLoadingPreview() {
    SquadifyTheme {
        MatchDetailsContent(
            uiState = MatchDetailsUiState.Loading,
            onNavigateBack = {}, onRetry = {}, onSortSelected = {}, onAddToCallUp = {}, onRemoveFromCallUp = {}, onEditMatch = {}, onDeleteMatch = {}, onEnterLiveMatch = {}, onOpenReport = {}
        )
    }
}

// ---- The call-up's cases (CallUpRules.kt): a 5-a-side team, six players in the pool ----

private val callUpSamplePlayers = listOf(
    Player("c1", "c1", "Ana", "Costa", LocalDate(2011, 5, 2), shirtNumber = 1, active = true, position = PlayerPosition.GOALKEEPER),
    Player("c2", "c1", "Rui", "Perestrelo", LocalDate(2011, 1, 9), shirtNumber = 4, active = true, position = PlayerPosition.DEFENDER),
    Player("c3", "c1", "Tomás", "Bragança", LocalDate(2011, 3, 12), shirtNumber = 10, active = true, position = PlayerPosition.MIDFIELD),
    Player("c4", "c1", "Duarte", "Nabais", LocalDate(2011, 7, 30), shirtNumber = 7, active = true, position = PlayerPosition.WINGER),
    Player("c5", "c1", "Beatriz", "Almeida", LocalDate(2011, 9, 18), shirtNumber = 9, active = true, position = PlayerPosition.STRIKER),
    Player("c6", "c1", "Xavier", "Boaventura", LocalDate(2011, 8, 4), active = true)
)

/** [saved] players already called up; [canManageCallUps] / [canManageLiveMatch] the viewer's rights. */
private fun callUpSample(saved: Int, canManageCallUps: Boolean, canManageLiveMatch: Boolean): MatchDetails {
    val base = sampleDetails(MatchStatus.SCHEDULED, canManage = canManageCallUps)
    return base.copy(
        team = base.team.copy(teamSize = 5),
        callUps = callUpSamplePlayers.take(saved).map {
            MatchCallUpWithPlayer(MatchCallUp(id = "cu-${it.id}", matchId = "m1", playerId = it.id, status = CallUpStatus.CALLED_UP), it)
        },
        poolPlayers = callUpSamplePlayers.drop(saved),
        canManageCallUps = canManageCallUps,
        canManageLiveMatch = canManageLiveMatch
    )
}

@Composable
private fun CallUpCasePreview(state: MatchDetailsUiState.Idle) {
    SquadifyTheme {
        MatchDetailsContent(
            uiState = state,
            onNavigateBack = {}, onRetry = {}, onSortSelected = {}, onAddToCallUp = {}, onRemoveFromCallUp = {}, onEditMatch = {}, onDeleteMatch = {}, onEnterLiveMatch = {}, onOpenReport = {}
        )
    }
}

/** Building the call-up, still below the 5 needed: "Save call-up" is disabled. */
@Preview
@Composable
private fun CallUpBelowMinimumPreview() =
    CallUpCasePreview(MatchDetailsUiState.Idle(callUpSample(saved = 3, canManageCallUps = true, canManageLiveMatch = true)))

/** A saved call-up with a player added since: "Save call-up". */
@Preview
@Composable
private fun CallUpUnsavedChangesPreview() {
    val details = callUpSample(saved = 5, canManageCallUps = true, canManageLiveMatch = true)
    CallUpCasePreview(MatchDetailsUiState.Idle(details, selectedIds = details.callUps.map { it.player.id }.toSet() + "c6"))
}

/** The call-up saved and unchanged: "Start match". */
@Preview
@Composable
private fun CallUpSavedPreview() =
    CallUpCasePreview(MatchDetailsUiState.Idle(callUpSample(saved = 5, canManageCallUps = true, canManageLiveMatch = true)))

/** Can start the game but not do the call-up, and it's ready: the call-up, read-only, and "Start match". */
@Preview
@Composable
private fun CallUpReadyForStarterPreview() =
    CallUpCasePreview(MatchDetailsUiState.Idle(callUpSample(saved = 5, canManageCallUps = false, canManageLiveMatch = true)))

/** Can start the game but not do the call-up, and it isn't ready: the details and "not ready yet". */
@Preview
@Composable
private fun CallUpNotReadyForStarterPreview() =
    CallUpCasePreview(MatchDetailsUiState.Idle(callUpSample(saved = 2, canManageCallUps = false, canManageLiveMatch = true)))

/** Neither does the call-up nor starts the game: the details only. */
@Preview
@Composable
private fun CallUpViewerPreview() =
    CallUpCasePreview(MatchDetailsUiState.Idle(callUpSample(saved = 5, canManageCallUps = false, canManageLiveMatch = false)))

