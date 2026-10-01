package com.ricardofrango.squadify.presentation.ui.matchreport

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchPlayerStats
import com.ricardofrango.squadify.model.MatchPlayerStatsWithPlayer
import com.ricardofrango.squadify.model.MatchReport
import com.ricardofrango.squadify.model.MatchReportDetails
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayerPosition
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.components.shortDateLabel
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreenLight
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.WhiteSmoke
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.*

/**
 * The post-match report of a finished match (design 24-26): an editor with Team / Opponent / Players
 * tabs for whoever can edit, and a one-scroll read view for everyone else who may see it.
 * [onNavigateBack] gets whether anything was saved, so the screens behind can reload.
 */
@Composable
fun MatchReportScreen(
    matchId: String,
    startEditing: Boolean,
    onNavigateBack: (changed: Boolean) -> Unit,
    viewModel: MatchReportViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(matchId) { viewModel.load(matchId, startEditing) }

    MatchReportContent(
        uiState = uiState,
        onNavigateBack = { onNavigateBack(viewModel.savedSomething) },
        onRetry = { viewModel.load(matchId, startEditing, force = true) },
        onStartEditing = viewModel::onStartEditing,
        onTabSelected = viewModel::onTabSelected,
        onFieldsChanged = viewModel::onFieldsChanged,
        onToggleRating = viewModel::onToggleRating,
        onRate = viewModel::onRate,
        onClearRating = viewModel::onClearRating,
        onRateNext = viewModel::onRateNext,
        onReadSortSelected = viewModel::onReadSortSelected,
        onSave = viewModel::onSave,
        onToastShown = viewModel::onToastShown
    )
}

@Composable
private fun MatchReportContent(
    uiState: MatchReportUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onStartEditing: () -> Unit,
    onTabSelected: (ReportTab) -> Unit,
    onFieldsChanged: ((ReportFields) -> ReportFields) -> Unit,
    onToggleRating: (String) -> Unit,
    onRate: (String, Int) -> Unit,
    onClearRating: (String) -> Unit,
    onRateNext: () -> Unit,
    onReadSortSelected: (ReportPlayerSort) -> Unit,
    onSave: (SaveAction) -> Unit,
    onToastShown: () -> Unit
) {
    val idle = uiState as? MatchReportUiState.Idle
    val snackbarHostState = remember { SnackbarHostState() }
    var showCompleteDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    // "Save draft" from the leave dialog: leave once that save lands.
    var leaveAfterSave by remember { mutableStateOf(false) }

    val hasUnsavedChanges = idle != null && idle.editing && idle.isDirty
    val requestBack = { if (hasUnsavedChanges) showLeaveDialog = true else onNavigateBack() }

    // System/gesture back with unsaved changes asks first; with none, it leaves silently (24e).
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = hasUnsavedChanges,
        onBackCompleted = { showLeaveDialog = true }
    )

    LaunchedEffect(leaveAfterSave, idle?.saving, idle?.isDirty, idle?.failedAction) {
        if (leaveAfterSave && idle != null && idle.saving == null) {
            if (!idle.isDirty) onNavigateBack() else if (idle.failedAction != null) leaveAfterSave = false
        }
    }

    // Toasts auto-dismiss; a failed save stays up with Retry until it's retried or succeeds (26e).
    LaunchedEffect(idle?.toast) {
        val toast = idle?.toast ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(getString(toast.message()), duration = SnackbarDuration.Short)
        onToastShown()
    }
    LaunchedEffect(idle?.failedAction, idle?.saveErrorMessage) {
        val failed = idle?.failedAction ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = idle.saveErrorMessage ?: getString(Res.string.msg_report_save_failed),
            actionLabel = getString(Res.string.btn_retry),
            duration = SnackbarDuration.Indefinite
        )
        if (result == SnackbarResult.ActionPerformed) onSave(failed)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight && maxWidth >= 600.dp
        Scaffold(
            containerColor = WhiteSmoke,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    ReportSnackbar(
                        message = data.visuals.message,
                        actionLabel = data.visuals.actionLabel,
                        onAction = { data.performAction() }
                    )
                }
            },
            topBar = {
                Column(Modifier.background(CarbonGray)) {
                    FMTopBar(
                        // The no-permission screen drops the subtitle so it doesn't reveal the match (26f).
                        title = stringResource(Res.string.report_title),
                        subtitle = idle?.let { subtitle(it.details) },
                        onNavigateBack = requestBack,
                        actions = {
                            when {
                                idle == null -> Unit
                                // Landscape editor: the tabs move into the header row (24c).
                                idle.editing && wide -> EditorTabs(idle, onTabSelected, compact = true)
                                idle.editing -> {
                                    EditorStatusChip(idle)
                                    Spacer(Modifier.width(12.dp))
                                }
                                idle.details.canEdit -> EditAction(onStartEditing)
                            }
                        }
                    )
                    if (idle != null) {
                        if (idle.editing) {
                            if (!wide) EditorTabs(idle, onTabSelected)
                        } else {
                            ReadStatusBand(idle)
                        }
                    }
                }
            },
            bottomBar = {
                if (idle != null && idle.editing && !wide) {
                    EditorActions(idle, wide = false, onSaveDraft = { onSave(SaveAction.DRAFT) }, onComplete = { showCompleteDialog = true })
                }
            }
        ) { padding ->
            val modifier = Modifier.fillMaxSize().padding(padding)
            when (uiState) {
                is MatchReportUiState.Loading -> LoadingSkeleton(modifier)
                is MatchReportUiState.Error -> FullScreenMessage(
                    icon = Res.drawable.ic_cloud_off,
                    title = stringResource(Res.string.msg_report_load_failed_title),
                    body = stringResource(Res.string.msg_report_load_failed_body),
                    buttonLabel = stringResource(Res.string.btn_retry),
                    primary = true,
                    onClick = onRetry,
                    modifier = modifier
                )
                is MatchReportUiState.NoPermission -> FullScreenMessage(
                    icon = Res.drawable.ic_lock,
                    title = stringResource(Res.string.msg_report_no_permission_title),
                    body = stringResource(Res.string.msg_report_no_permission_body),
                    buttonLabel = stringResource(Res.string.report_back_to_match),
                    primary = false,
                    onClick = onNavigateBack,
                    modifier = modifier
                )
                is MatchReportUiState.Idle -> if (uiState.editing) {
                    Row(modifier) {
                        EditorBody(uiState, wide, onFieldsChanged, onToggleRating, onRate, onClearRating, onRateNext, Modifier.weight(1f))
                        if (wide) EditorActions(uiState, wide = true, onSaveDraft = { onSave(SaveAction.DRAFT) }, onComplete = { showCompleteDialog = true })
                    }
                } else {
                    Box(modifier) { ReadView(uiState, wide, onReadSortSelected) }
                }
            }
        }
    }

    if (showCompleteDialog && idle != null) {
        ReportDialog(
            title = stringResource(Res.string.report_complete_dialog_title),
            body = if (idle.unratedCount > 0) pluralStringResource(Res.plurals.report_complete_dialog_body_unrated, idle.unratedCount, idle.unratedCount)
            else stringResource(Res.string.report_complete_dialog_body),
            onDismiss = { showCompleteDialog = false }
        ) {
            // Two equal buttons side by side (24a).
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogSecondaryButton(stringResource(Res.string.report_keep_editing), Modifier.weight(1f)) { showCompleteDialog = false }
                DialogPrimaryButton(stringResource(Res.string.report_complete_confirm), Modifier.weight(1f)) {
                    showCompleteDialog = false; onSave(SaveAction.COMPLETE)
                }
            }
        }
    }

    if (showLeaveDialog) {
        ReportDialog(
            title = stringResource(Res.string.report_leave_dialog_title),
            body = stringResource(Res.string.report_leave_dialog_body),
            onDismiss = { showLeaveDialog = false }
        ) {
            // Save draft full width, then Discard / Keep editing side by side (24a).
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogPrimaryButton(stringResource(Res.string.report_save_draft), Modifier.fillMaxWidth()) {
                    showLeaveDialog = false; leaveAfterSave = true; onSave(SaveAction.DRAFT)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DialogSecondaryButton(stringResource(Res.string.report_discard), Modifier.weight(1f), destructive = true) {
                        showLeaveDialog = false; onNavigateBack()
                    }
                    DialogSecondaryButton(stringResource(Res.string.report_keep_editing), Modifier.weight(1f)) { showLeaveDialog = false }
                }
            }
        }
    }
}

private fun ReportToast.message() = when (this) {
    ReportToast.DRAFT_SAVED -> Res.string.report_toast_draft_saved
    ReportToast.REPORT_COMPLETED -> Res.string.report_toast_completed
    ReportToast.REPORT_UPDATED -> Res.string.report_toast_updated
}

@Composable
private fun subtitle(details: MatchReportDetails): String {
    val date = details.match.dateTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return stringResource(Res.string.report_subtitle, details.teamName, details.opponentName, shortDateLabel(date))
}

/** The editor's text-only chip: Not saved / Completed / New / Draft (24a, 26c, 26e). */
@Composable
private fun EditorStatusChip(state: MatchReportUiState.Idle) {
    when {
        state.failedAction != null -> HeaderStatusChip(stringResource(Res.string.report_status_not_saved), ChipTone.RED, showDot = false)
        state.isCompleted -> HeaderStatusChip(stringResource(Res.string.report_status_completed), ChipTone.GREEN, showDot = false)
        state.isBrandNew -> HeaderStatusChip(stringResource(Res.string.report_status_new), ChipTone.AMBER, showDot = false)
        else -> HeaderStatusChip(stringResource(Res.string.report_status_draft), ChipTone.AMBER, showDot = false)
    }
}

/** Pencil + "Edit" in the read view's top bar, for whoever can edit (25a). */
@Composable
private fun EditAction(onClick: () -> Unit) {
    Row(
        Modifier.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(Res.drawable.ic_edit), contentDescription = null, tint = PitchGreenLight, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(Res.string.label_edit), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = PitchGreenLight)
    }
}

/** "Last updated 28 Sep by Nuno Ferreira" (completed) or "Not completed yet · saved ..." (a draft). */
@Composable
internal fun reportStatusLine(report: MatchReport?, updatedByName: String?, today: LocalDate): String {
    if (report == null) return stringResource(Res.string.report_status_line_none)
    val date = report.updatedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val name = updatedByName.orEmpty()
    val completed = report.completedAt != null
    return when {
        completed && date == today -> stringResource(Res.string.report_status_line_completed_today, name)
        completed -> stringResource(Res.string.report_status_line_completed, shortDateLabel(date), name)
        date == today -> stringResource(Res.string.report_status_line_draft_today, name)
        else -> stringResource(Res.string.report_status_line_draft, shortDateLabel(date), name)
    }
}

/** Read view: the Completed/Pending chip and "Last updated ..." on one row under the title (25a). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadStatusBand(state: MatchReportUiState.Idle) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (state.isCompleted) HeaderStatusChip(stringResource(Res.string.report_status_completed), ChipTone.GREEN)
        else HeaderStatusChip(stringResource(Res.string.report_status_pending), ChipTone.AMBER)
        // Wraps to a second line rather than truncating when it's long (25b).
        Text(
            reportStatusLine(state.details.report, state.details.updatedByName, today),
            modifier = Modifier.align(Alignment.CenterVertically),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.72f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDialog(title: String, body: String, onDismiss: () -> Unit, buttons: @Composable () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
            Column(Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = DeepCarbon)
                Spacer(Modifier.height(10.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                Spacer(Modifier.height(20.dp))
                buttons()
            }
        }
    }
}

@Composable
private fun DialogPrimaryButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White),
        shape = RoundedCornerShape(14.dp)
    ) { Text(label, fontWeight = FontWeight.Black, textAlign = TextAlign.Center) }
}

@Composable
private fun DialogSecondaryButton(label: String, modifier: Modifier, destructive: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        border = BorderStroke(1.5.dp, if (destructive) AlertRed.copy(alpha = 0.4f) else CarbonGray.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (destructive) AlertRed else CarbonGray, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** Dark snackbar: a check for confirmations (24e toast), a green Retry for a failed save (26e). */
@Composable
private fun ReportSnackbar(message: String, actionLabel: String?, onAction: () -> Unit) {
    Surface(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = CarbonGray) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (actionLabel == null) {
                Icon(painterResource(Res.drawable.ic_check), contentDescription = null, tint = PitchGreenLight, modifier = Modifier.size(18.dp))
            } else {
                // A failure: red circle with "!" (26e).
                Box(Modifier.size(20.dp).background(Color(0xFFFF8A80), CircleShape), contentAlignment = Alignment.Center) {
                    Text("!", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black), color = CarbonGray)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
            if (actionLabel != null) {
                Text(
                    actionLabel,
                    modifier = Modifier.clickable(onClick = onAction).padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = PitchGreenLight
                )
            }
        }
    }
}

/** Skeleton of the report while it loads (26a): text lines, then player rows with a square rating. Pulses. */
@Composable
private fun LoadingSkeleton(modifier: Modifier) {
    val description = stringResource(Res.string.report_loading)
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0.05f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
    )
    val shade = CarbonGray.copy(alpha = pulse)
    @Composable
    fun Bar(width: Float, height: Int = 10) =
        Box(Modifier.fillMaxWidth(width).height(height.dp).background(shade, RoundedCornerShape(5.dp)))
    Column(
        modifier.background(WhiteSmoke).padding(16.dp).semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ReportCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Bar(0.4f, 12) }
                Box(Modifier.size(width = 90.dp, height = 38.dp).background(shade, RoundedCornerShape(12.dp)))
            }
            repeat(2) {
                Spacer(Modifier.height(20.dp)); Bar(0.4f)
                Spacer(Modifier.height(10.dp)); Bar(1f); Spacer(Modifier.height(8.dp)); Bar(0.8f)
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = ReportCardShape, color = Color.White, border = ReportCardBorder) {
            Column {
                Column(Modifier.padding(16.dp)) { Bar(0.25f); Spacer(Modifier.height(8.dp)); Bar(0.4f) }
                repeat(4) {
                    HorizontalDivider(color = CarbonGray.copy(alpha = 0.06f))
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(width = 22.dp, height = 14.dp).background(shade, RoundedCornerShape(5.dp)))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) { Bar(0.6f); Spacer(Modifier.height(8.dp)); Bar(0.35f) }
                        Box(Modifier.size(52.dp).background(shade, RoundedCornerShape(14.dp)))
                    }
                }
            }
        }
    }
}

/** The app's full-screen empty-state pattern: icon in a grey circle, title, one line, a button (26b/26f). */
@Composable
private fun FullScreenMessage(
    icon: DrawableResource,
    title: String,
    body: String,
    buttonLabel: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier.background(WhiteSmoke).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(84.dp).background(CarbonGray.copy(alpha = 0.06f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = SecondaryText, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = SecondaryText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        if (primary) {
            Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                Text(buttonLabel, fontWeight = FontWeight.Black)
            }
        } else {
            OutlinedButton(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
                Text(buttonLabel, color = CarbonGray, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---- Previews ----

private fun previewRow(id: String, number: Int, first: String, last: String, pos: PlayerPosition?, started: Boolean, minutes: Int, goals: Int = 0, assists: Int = 0, ownGoals: Int = 0, yellows: Int = 0, rating: Int? = null) =
    MatchPlayerStatsWithPlayer(
        MatchPlayerStats("m1", id, started, minutes, goals, assists, ownGoals, yellows, 0, rating),
        Player(id, "c1", first, last, LocalDate(2011, 3, 1), shirtNumber = number, active = true, position = pos)
    )

private fun previewDetails(report: MatchReport?, withStats: Boolean = true, rated: Boolean = false) = MatchReportDetails(
    match = Match("m1", "c1", "t1", "o1", dateTime = Instant.parse("2026-09-27T10:00:00Z"), status = MatchStatus.FINISHED, halfLengthMinutes = 35, fullTimeMinute = 70),
    teamName = "Sub-15 A",
    opponentName = "AD Fontelas",
    report = report,
    updatedByName = report?.let { "Nuno Ferreira" },
    playerStats = if (!withStats) emptyList() else listOf(
        previewRow("p1", 1, "Rui", "Perestrelo", PlayerPosition.GOALKEEPER, true, 70, rating = if (rated) 7 else null),
        previewRow("p3", 3, "Henrique", "Vaz", PlayerPosition.DEFENDER, true, 70, ownGoals = 1, rating = if (rated) 5 else null),
        previewRow("p6", 6, "Gil", "Bettencourt", PlayerPosition.MIDFIELD, true, 70, goals = 1, assists = 1, rating = if (rated) 8 else null),
        previewRow("p10", 10, "Tomás", "Bragança", PlayerPosition.MIDFIELD, true, 70, goals = 2, rating = if (rated) 9 else null),
        previewRow("p14", 14, "Caetano", "Lousada", null, false, 22, yellows = 1)
    ),
    canEdit = true
)

private val previewReport = MatchReport(
    matchId = "m1",
    tactic = "4-3-3",
    positiveAspects = "Pressed high for the first 30 minutes and won the ball back in their half six times.",
    aspectsToImprove = "Communication at corners. Second balls in midfield.",
    opponentBestPlayers = "Their #10, quick on the turn.",
    completedAt = Instant.parse("2026-09-28T09:00:00Z"),
    updatedAt = Instant.parse("2026-09-28T09:00:00Z"),
    updatedByUserId = "u1"
)

private fun previewIdle(details: MatchReportDetails, editing: Boolean, tab: ReportTab = ReportTab.PLAYERS, open: String? = null): MatchReportUiState.Idle {
    val ratings = details.playerStats.associate { it.player.id to it.stats.rating }
    return MatchReportUiState.Idle(
        details = details, editing = editing,
        originalFields = ReportFields.from(details.report), fields = ReportFields.from(details.report),
        originalRatings = ratings, ratings = ratings, tab = tab, openRatingFor = open
    )
}

@Composable
private fun PreviewContent(state: MatchReportUiState) = SquadifyTheme {
    MatchReportContent(
        uiState = state, onNavigateBack = {}, onRetry = {}, onStartEditing = {}, onTabSelected = {}, onFieldsChanged = {},
        onToggleRating = {}, onRate = { _, _ -> }, onClearRating = {}, onRateNext = {}, onReadSortSelected = {}, onSave = {}, onToastShown = {}
    )
}

@Preview
@Composable
private fun MatchReportEditorNewPreview() = PreviewContent(previewIdle(previewDetails(null), editing = true))

@Preview
@Composable
private fun MatchReportEditorRatingOpenPreview() = PreviewContent(previewIdle(previewDetails(previewReport.copy(completedAt = null), rated = false), editing = true, open = "p1"))

@Preview
@Composable
private fun MatchReportEditorTeamTabPreview() = PreviewContent(previewIdle(previewDetails(previewReport), editing = true, tab = ReportTab.TEAM))

@Preview(widthDp = 900, heightDp = 420)
@Composable
private fun MatchReportEditorLandscapePreview() = PreviewContent(previewIdle(previewDetails(previewReport, rated = true), editing = true))

@Preview
@Composable
private fun MatchReportReadPreview() = PreviewContent(previewIdle(previewDetails(previewReport, rated = true), editing = false))

@Preview(widthDp = 900, heightDp = 420)
@Composable
private fun MatchReportReadLandscapePreview() = PreviewContent(previewIdle(previewDetails(previewReport, rated = true), editing = false))

@Preview
@Composable
private fun MatchReportLegacyMatchPreview() = PreviewContent(previewIdle(previewDetails(null, withStats = false), editing = true, tab = ReportTab.TEAM))

@Preview
@Composable
private fun MatchReportLoadingPreview() = PreviewContent(MatchReportUiState.Loading)

@Preview
@Composable
private fun MatchReportErrorPreview() = PreviewContent(MatchReportUiState.Error("x"))

@Preview
@Composable
private fun MatchReportNoPermissionPreview() = PreviewContent(MatchReportUiState.NoPermission)
