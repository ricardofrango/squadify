package com.ricardofrango.squadify.presentation.ui.matchreport

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.MatchPlayerStatsWithPlayer
import com.ricardofrango.squadify.presentation.ui.theme.Amber
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.WhiteSmoke
import androidx.compose.material3.Icon
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.*

/** Tabs sit on the dark header: Team · Opponent · Players (the last carries an amber unrated count). */
@Composable
internal fun EditorTabs(state: MatchReportUiState.Idle, onTabSelected: (ReportTab) -> Unit, compact: Boolean = false) {
    val tabs = buildList {
        add(ReportTab.TEAM); add(ReportTab.OPPONENT)
        if (state.hasStats) add(ReportTab.PLAYERS) // removed, not greyed, when there are no stats (26d)
    }
    // Portrait: three equal tabs under the title. Landscape (24c): compact tabs in the header row.
    Row(if (compact) Modifier else Modifier.fillMaxWidth().background(CarbonGray)) {
        tabs.forEach { tab ->
            val selected = tab == state.tab
            Column(
                (if (compact) Modifier.width(IntrinsicSize.Max) else Modifier.weight(1f))
                    .clickable { onTabSelected(tab) }
                    .padding(top = 10.dp, start = if (compact) 12.dp else 0.dp, end = if (compact) 12.dp else 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(tab.label()),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.6f)
                    )
                    if (tab == ReportTab.PLAYERS && state.unratedCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(10.dp), color = Amber) {
                            Text(
                                state.unratedCount.toString(),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                color = CarbonGray
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).background(if (selected) PitchGreen else Color.Transparent))
            }
        }
    }
}

private fun ReportTab.label(): StringResource = when (this) {
    ReportTab.TEAM -> Res.string.report_tab_team
    ReportTab.OPPONENT -> Res.string.report_tab_opponent
    ReportTab.PLAYERS -> Res.string.report_tab_players
}

/** One labelled field of the report: the tactic is a single line, everything else multi-line. */
private data class FieldSpec(
    val label: StringResource,
    val hint: StringResource,
    val singleLine: Boolean,
    val get: (ReportFields) -> String,
    val set: (ReportFields, String) -> ReportFields
)

private val TeamFields = listOf(
    FieldSpec(Res.string.report_field_tactic, Res.string.report_hint_tactic, true, { it.tactic }, { f, v -> f.copy(tactic = v) }),
    FieldSpec(Res.string.report_field_positive, Res.string.report_hint_optional, false, { it.positiveAspects }, { f, v -> f.copy(positiveAspects = v) }),
    FieldSpec(Res.string.report_field_less_positive, Res.string.report_hint_optional, false, { it.lessPositiveAspects }, { f, v -> f.copy(lessPositiveAspects = v) }),
    FieldSpec(Res.string.report_field_to_improve, Res.string.report_hint_optional, false, { it.aspectsToImprove }, { f, v -> f.copy(aspectsToImprove = v) }),
    FieldSpec(Res.string.report_field_team_notes, Res.string.report_hint_optional, false, { it.teamNotes }, { f, v -> f.copy(teamNotes = v) })
)

private val OpponentFields = listOf(
    FieldSpec(Res.string.report_field_strengths, Res.string.report_hint_optional, false, { it.opponentStrengths }, { f, v -> f.copy(opponentStrengths = v) }),
    FieldSpec(Res.string.report_field_weaknesses, Res.string.report_hint_optional, false, { it.opponentWeaknesses }, { f, v -> f.copy(opponentWeaknesses = v) }),
    FieldSpec(Res.string.report_field_best_players, Res.string.report_hint_optional, false, { it.opponentBestPlayers }, { f, v -> f.copy(opponentBestPlayers = v) }),
    FieldSpec(Res.string.report_field_additional_info, Res.string.report_hint_optional, false, { it.opponentAdditionalInfo }, { f, v -> f.copy(opponentAdditionalInfo = v) })
)

@Composable
internal fun EditorBody(
    state: MatchReportUiState.Idle,
    wide: Boolean,
    onFieldsChanged: ((ReportFields) -> ReportFields) -> Unit,
    onToggleRating: (String) -> Unit,
    onRate: (String, Int) -> Unit,
    onClearRating: (String) -> Unit,
    onRateNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state.tab) {
        ReportTab.TEAM -> FieldsTab(
            title = stringResource(Res.string.report_section_our_team),
            specs = TeamFields,
            fields = state.fields,
            filled = state.fields.teamFilled,
            showNoStatsNotice = !state.hasStats,
            wide = wide,
            onFieldsChanged = onFieldsChanged,
            modifier = modifier
        )
        ReportTab.OPPONENT -> FieldsTab(
            title = state.details.opponentName,
            specs = OpponentFields,
            fields = state.fields,
            filled = state.fields.opponentFilled,
            showNoStatsNotice = false,
            wide = wide,
            onFieldsChanged = onFieldsChanged,
            modifier = modifier
        )
        ReportTab.PLAYERS -> PlayersTab(state, wide, onToggleRating, onRate, onClearRating, onRateNext, modifier)
    }
}

@Composable
private fun FieldsTab(
    title: String,
    specs: List<FieldSpec>,
    fields: ReportFields,
    filled: Int,
    showNoStatsNotice: Boolean,
    wide: Boolean,
    onFieldsChanged: ((ReportFields) -> ReportFields) -> Unit,
    modifier: Modifier
) {
    LazyColumn(modifier.fillMaxSize().background(WhiteSmoke), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showNoStatsNotice) item { NoStatsNotice() }
        item {
            // Portrait: the fields sit in a card. Landscape: straight on the background, in two columns (26h).
            val container: @Composable (@Composable () -> Unit) -> Unit =
                if (wide) { inner -> Column { inner() } } else { inner -> ReportCard { inner() } }
            container {
                if (!wide) {
                    SectionLabel(title, trailing = stringResource(Res.string.report_fields_filled, filled, specs.size))
                    Spacer(Modifier.height(12.dp))
                }
                // Landscape: two columns of fields (design 24c).
                val rows = if (wide) specs.chunked(2) else specs.map { listOf(it) }
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { spec ->
                                ReportTextField(
                                    spec = spec,
                                    value = spec.get(fields),
                                    onValueChange = { v -> onFieldsChanged { spec.set(it, v) } },
                                    modifier = Modifier.weight(1f),
                                    narrow = spec.singleLine && !wide,
                                    // Landscape starts every field one line tall; they grow as you type (26h).
                                    compact = wide
                                )
                            }
                            if (row.size == 1 && wide) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportTextField(spec: FieldSpec, value: String, onValueChange: (String) -> Unit, modifier: Modifier, narrow: Boolean = false, compact: Boolean = false) {
    Column(modifier) {
        Text(stringResource(spec.label), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = SecondaryText)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            // The tactic is a short value ("4-3-3"): a narrow field in portrait (24a).
            modifier = if (narrow) Modifier.widthIn(max = 220.dp).fillMaxWidth() else Modifier.fillMaxWidth(),
            textStyle = if (narrow) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyLarge,
            placeholder = { Text(stringResource(spec.hint), color = DeepCarbon.copy(alpha = 0.45f)) },
            singleLine = spec.singleLine,
            minLines = if (spec.singleLine || compact) 1 else 3,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PitchGreen,
                unfocusedBorderColor = CarbonGray.copy(alpha = 0.16f),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = DeepCarbon,
                unfocusedTextColor = DeepCarbon,
                cursorColor = PitchGreen
            )
        )
    }
}

/** One paragraph with an info icon, the lead sentence bold and inline (26d/26h). */
@Composable
internal fun NoStatsNotice() {
    val title = stringResource(Res.string.report_no_stats_title)
    val body = stringResource(Res.string.report_no_stats_body)
    ReportCard {
        Row {
            Icon(painterResource(Res.drawable.ic_info), contentDescription = null, tint = SecondaryText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = DeepCarbon)) { append(title) }
                    append(" ")
                    append(body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = DeepCarbon.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun PlayersTab(
    state: MatchReportUiState.Idle,
    wide: Boolean,
    onToggleRating: (String) -> Unit,
    onRate: (String, Int) -> Unit,
    onClearRating: (String) -> Unit,
    onRateNext: () -> Unit,
    modifier: Modifier
) {
    val total = state.players.size
    LazyColumn(modifier.fillMaxSize().background(WhiteSmoke), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!wide) {
            item { UnratedProgress(state.unratedCount, total, onRateNext) }
        }
        if (state.isBrandNew) {
            item { NewReportHint(onRateNext) }
        }
        item {
            Surface(Modifier.fillMaxWidth(), shape = ReportCardShape, color = Color.White, border = ReportCardBorder) {
                Column {
                    state.players.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(color = CarbonGray.copy(alpha = 0.06f))
                        PlayerRatingRow(
                            row = row,
                            rating = state.ratings[row.player.id],
                            open = !wide && state.openRatingFor == row.player.id,
                            wide = wide,
                            onToggle = { onToggleRating(row.player.id) },
                            onRate = { onRate(row.player.id, it) },
                            onClear = { onClearRating(row.player.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun UnratedProgress(unrated: Int, total: Int, onRateNext: (() -> Unit)?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (unrated > 0) pluralStringResource(Res.plurals.report_unrated_count, unrated, unrated) else stringResource(Res.string.report_all_rated),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (unrated > 0) AmberText else PitchGreen,
                modifier = Modifier.weight(1f)
            )
            if (unrated > 0 && onRateNext != null) {
                Text(
                    stringResource(Res.string.report_rate_next),
                    modifier = Modifier.clickable(onClick = onRateNext).padding(4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = PitchGreen
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else (total - unrated).toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = PitchGreen,
            trackColor = CarbonGray.copy(alpha = 0.08f),
            drawStopIndicator = {}
        )
    }
}

@Composable
private fun NewReportHint(onRateFirst: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = ReportCardShape, color = Color.White, border = BorderStroke(1.dp, PitchGreen.copy(alpha = 0.3f))) {
        Row(Modifier.padding(16.dp)) {
            Box(Modifier.size(36.dp).background(PitchGreen.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Text("★", color = PitchGreen, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(Res.string.report_new_hint_title), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.report_new_hint_body), style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.report_new_hint_action),
                    modifier = Modifier.clickable(onClick = onRateFirst).padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = PitchGreen
                )
            }
        }
    }
}

/**
 * One player's row. Portrait: a compact value button that opens a 6×2 grid of 44dp targets beneath
 * the row (design 24b, "tap to open, pick, auto-advance"). Landscape: the full 0-10 row always
 * visible beneath the name, so every rating is one tap (24c); tapping the selected value clears it.
 */
@Composable
private fun PlayerRatingRow(
    row: MatchPlayerStatsWithPlayer,
    rating: Int?,
    open: Boolean,
    wide: Boolean,
    onToggle: () -> Unit,
    onRate: (Int) -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.background(if (open) PitchGreen.copy(alpha = 0.05f) else Color.White).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerStatsHeader(row, Modifier.weight(1f), inline = wide)
            if (!wide) {
                Spacer(Modifier.width(8.dp))
                RatingButton(rating, onToggle)
            }
        }
        if (wide) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..10).forEach { value ->
                    RatingCell(value.toString(), selected = rating == value, modifier = Modifier.weight(1f)) {
                        if (rating == value) onClear() else onRate(value)
                    }
                }
            }
        } else if (open) {
            Spacer(Modifier.height(12.dp))
            val cells: List<Int?> = (0..10).toList() + listOf(null) // null = Clear
            // Lines up under the name, past the shirt-number column (24d).
            Column(Modifier.padding(start = 30.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cells.chunked(6).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        line.forEach { value ->
                            if (value == null) {
                                RatingCell(stringResource(Res.string.report_clear), selected = false, small = true, modifier = Modifier.weight(1f), onClick = onClear)
                            } else {
                                RatingCell(value.toString(), selected = rating == value, modifier = Modifier.weight(1f)) { onRate(value) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Unrated: a dashed green "Rate" pill. Rated: the value on a dark pill. */
@Composable
private fun RatingButton(rating: Int?, onClick: () -> Unit) {
    // Both states are the same 56dp square, so rows don't shift when a rating is picked (24a).
    if (rating == null) {
        Box(
            Modifier.size(56.dp).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawRoundRect(
                    color = PitchGreen.copy(alpha = 0.55f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                )
            }
            Text(
                stringResource(Res.string.report_rate),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = PitchGreen,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Surface(onClick = onClick, modifier = Modifier.size(56.dp), shape = RoundedCornerShape(14.dp), color = CarbonGray) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    rating.toString(),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun RatingCell(label: String, selected: Boolean, modifier: Modifier = Modifier, small: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .background(if (selected) PitchGreen else Color.White, RoundedCornerShape(11.dp))
            .border(1.5.dp, if (selected) PitchGreen else CarbonGray.copy(alpha = 0.16f), RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = if (small) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
            color = if (selected) Color.White else if (small) SecondaryText else DeepCarbon
        )
    }
}

/**
 * Save draft / Complete report. Portrait: a bottom bar, the primary given 1.25× the width. Landscape:
 * a right rail with the unrated count above the buttons (design 24c). While either save is in
 * flight, the pressed button shows a spinner and both are locked.
 */
@Composable
internal fun EditorActions(state: MatchReportUiState.Idle, wide: Boolean, onSaveDraft: () -> Unit, onComplete: () -> Unit) {
    val draftLabel = if (state.saving == SaveAction.DRAFT) stringResource(Res.string.report_saving) else stringResource(Res.string.report_save_draft)
    val completeLabel = when {
        state.saving == SaveAction.COMPLETE -> stringResource(Res.string.report_completing)
        state.isCompleted -> stringResource(Res.string.report_update)
        else -> stringResource(Res.string.report_complete)
    }
    val draft = @Composable { modifier: Modifier ->
        OutlinedButton(
            onClick = onSaveDraft,
            enabled = state.canSaveDraft,
            modifier = modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, CarbonGray.copy(alpha = 0.22f)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (state.saving == SaveAction.DRAFT) {
                CircularProgressIndicator(Modifier.size(16.dp), color = CarbonGray, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(draftLabel, color = CarbonGray, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
    val complete = @Composable { modifier: Modifier ->
        Button(
            onClick = onComplete,
            enabled = state.canComplete,
            modifier = modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (state.saving == SaveAction.COMPLETE) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(completeLabel, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
    }
    if (wide) {
        Box(Modifier.fillMaxHeight().width(1.dp).background(CarbonGray.copy(alpha = 0.08f)))
        Column(
            Modifier.fillMaxHeight().width(240.dp).background(Color.White).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.hasStats) UnratedProgress(state.unratedCount, state.players.size, onRateNext = null)
            Spacer(Modifier.weight(1f))
            draft(Modifier.fillMaxWidth())
            complete(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            draft(Modifier.weight(1f))
            complete(Modifier.weight(1.25f))
        }
    }
}
