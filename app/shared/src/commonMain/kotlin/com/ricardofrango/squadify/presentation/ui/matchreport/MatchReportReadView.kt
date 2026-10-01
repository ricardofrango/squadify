package com.ricardofrango.squadify.presentation.ui.matchreport

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
import androidx.compose.foundation.lazy.LazyColumn
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
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.WhiteSmoke
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.*

/**
 * The reading counterpart to the editor (design 25): one scroll, not tabs -- someone reading wants
 * the whole report in order. Empty fields are left out; a section with nothing in it collapses to a
 * single "nothing written" line. Landscape splits into two columns, analysis left, players right.
 */
@Composable
internal fun ReadView(state: MatchReportUiState.Idle, wide: Boolean, onSortSelected: (ReportPlayerSort) -> Unit) {
    if (wide) {
        Row(Modifier.fillMaxSize().background(WhiteSmoke)) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { TeamReadCard(state) }
                item { OpponentReadCard(state) }
            }
            LazyColumn(Modifier.width(380.dp), contentPadding = PaddingValues(top = 16.dp, end = 16.dp, bottom = 16.dp)) {
                item { PlayersReadCard(state, onSortSelected) }
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().background(WhiteSmoke), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { TeamReadCard(state) }
            item { OpponentReadCard(state) }
            item { PlayersReadCard(state, onSortSelected) }
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
            Surface(shape = RoundedCornerShape(12.dp), color = CarbonGray) {
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

@Composable
private fun PlayersReadCard(state: MatchReportUiState.Idle, onSortSelected: (ReportPlayerSort) -> Unit) {
    if (!state.hasStats) {
        ReportCard {
            SectionLabel(stringResource(Res.string.report_tab_players))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.report_no_stats_title), style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        }
        return
    }
    val summary = ratingSummary(state.players.map { state.ratings[it.player.id] })
    Surface(Modifier.fillMaxWidth(), shape = ReportCardShape, color = Color.White, border = ReportCardBorder) {
        Column {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel(stringResource(Res.string.report_tab_players))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (summary.average != null) stringResource(Res.string.report_rated_summary, summary.rated, summary.total, summary.average)
                        else stringResource(Res.string.report_rated_summary_no_avg, summary.rated, summary.total),
                        style = MaterialTheme.typography.labelMedium, color = SecondaryText
                    )
                }
                SortToggle(state.readSort, onSortSelected)
            }
            readOrder(state.players, state.ratings, state.readSort).forEach { row ->
                HorizontalDivider(color = CarbonGray.copy(alpha = 0.06f))
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlayerStatsHeader(row, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    RatingBadge(state.ratings[row.player.id])
                }
            }
        }
    }
}

@Composable
private fun SortToggle(sort: ReportPlayerSort, onSortSelected: (ReportPlayerSort) -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    Row(Modifier.border(1.5.dp, CarbonGray.copy(alpha = 0.16f), shape)) {
        listOf(ReportPlayerSort.RATING to Res.string.report_sort_rating, ReportPlayerSort.NUMBER to Res.string.report_sort_number)
            .forEach { (value, label) ->
                val selected = value == sort
                Box(
                    Modifier
                        .background(if (selected) CarbonGray else Color.Transparent, shape)
                        .clickable { onSortSelected(value) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (selected) Color.White else DeepCarbon
                    )
                }
            }
    }
}
