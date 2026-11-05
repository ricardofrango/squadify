package com.ricardofrango.squadify.presentation.ui.matchreport

import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.presentation.ui.components.FilterChipRow
import com.ricardofrango.squadify.presentation.ui.components.RatingPill
import com.ricardofrango.squadify.presentation.ui.components.readableListPadding
import com.ricardofrango.squadify.presentation.ui.components.twoDecimals
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.*

/**
 * The reading counterpart to the editor (design 25), in the editor's tabs (the developer, 2026-11-05):
 * Players (the team's average ranking, the sort, then each player's), Team and Opponent. Empty fields
 * are left out; a section with nothing in it collapses to a single "nothing written" line. On a wide
 * screen the tab keeps a readable width, centred.
 */
@Composable
internal fun ReadView(state: MatchReportUiState.Idle, onSortSelected: (ReportPlayerSort) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(ScreenBackground)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = readableListPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state.tab) {
                ReportTab.PLAYERS -> playersRead(state, onSortSelected)
                ReportTab.TEAM -> item { TeamReadCard(state) }
                ReportTab.OPPONENT -> item { OpponentReadCard(state) }
            }
        }
    }
}

@Composable
private fun TeamReadCard(state: MatchReportUiState.Idle) {
    val f = state.fields
    val texts = listOf(
        Res.string.report_field_positive to f.positiveAspects,
        Res.string.report_field_less_positive to f.lessPositiveAspects,
        Res.string.report_field_to_improve to f.aspectsToImprove,
        Res.string.report_field_team_notes to f.teamNotes
    ).filter { it.second.isNotBlank() }
    ReportCard {
        CardHeaderWithTactic(stringResource(Res.string.report_section_our_team), f.tactic)
        if (f.tactic.isBlank() && texts.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(Res.string.report_nothing_team), style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        } else {
            ReadFields(texts)
        }
    }
}

@Composable
private fun OpponentReadCard(state: MatchReportUiState.Idle) {
    val f = state.fields
    val texts = listOf(
        Res.string.report_field_strengths to f.opponentStrengths,
        Res.string.report_field_weaknesses to f.opponentWeaknesses,
        Res.string.report_field_best_players to f.opponentBestPlayers,
        Res.string.report_field_additional_info to f.opponentAdditionalInfo
    ).filter { it.second.isNotBlank() }
    ReportCard {
        CardHeaderWithTactic(state.details.opponentName, f.opponentTactic)
        if (f.opponentTactic.isBlank() && texts.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(Res.string.report_nothing_opponent), style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        } else {
            ReadFields(texts)
        }
    }
}

/**
 * A section header with its tactic at display size on the right (25a): the one-glance answer to "how
 * did they set up", for our team and the opponent alike. No chip when the tactic wasn't written.
 */
@Composable
private fun CardHeaderWithTactic(title: String, tactic: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SectionLabel(title) }
        if (tactic.isNotBlank()) {
            Text(stringResource(Res.string.report_field_tactic), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = SecondaryText)
            Spacer(Modifier.width(10.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = ClubChrome) {
                Text(
                    tactic,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ReadFields(texts: List<Pair<StringResource, String>>) {
    // Each field sits under a hairline divider (25a).
    Column {
        texts.forEach { (label, text) ->
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = CarbonGray.copy(alpha = 0.08f))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = SecondaryText)
            Spacer(Modifier.height(3.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = DeepCarbon)
        }
    }
}

private fun LazyListScope.playersRead(state: MatchReportUiState.Idle, onSortSelected: (ReportPlayerSort) -> Unit) {
    if (!state.hasStats) {
        item {
            ReportCard {
                SectionLabel(stringResource(Res.string.report_tab_players))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.report_no_stats_title), style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
            }
        }
        return
    }
    item { TeamAverageCard(ratingSummary(state.players.map { state.ratings[it.player.id] }).averageValue) }
    item {
        FilterChipRow(
            options = ReportPlayerSort.entries,
            selected = state.readSort,
            label = { stringResource(it.labelRes()) },
            onSelect = onSortSelected,
            insidePaddedContent = true,
            leadingLabel = stringResource(Res.string.label_sort)
        )
    }
    item {
        Surface(Modifier.fillMaxWidth(), shape = ReportCardShape, color = Color.White, border = ReportCardBorder) {
            Column {
                readOrder(state.players, state.ratings, state.readSort).forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = CarbonGray.copy(alpha = 0.06f))
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayerStatsHeader(row, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        RatingBadge(state.ratings[row.player.id])
                    }
                }
            }
        }
    }
}

/** The team's average ranking for the game: every rated player's, two decimals; a dash with nobody rated. */
@Composable
private fun TeamAverageCard(average: Double?) {
    ReportCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.report_team_average),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon,
                modifier = Modifier.weight(1f)
            )
            if (average != null) {
                RatingPill(average, average.twoDecimals(), minWidth = 64.dp, height = 36.dp, fontSize = 18.sp, corner = 18.dp)
            } else {
                Text("—", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = SecondaryText)
            }
        }
    }
}

private fun ReportPlayerSort.labelRes(): StringResource = when (this) {
    ReportPlayerSort.RATING -> Res.string.record_leaders_rating
    ReportPlayerSort.POSITION -> Res.string.label_position_field
    ReportPlayerSort.NUMBER -> Res.string.label_shirt_number
}
