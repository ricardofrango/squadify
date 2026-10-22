package com.ricardofrango.squadify.presentation.ui.teamdetails

import com.ricardofrango.squadify.presentation.ui.components.emptyStatsRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.model.FormResult
import com.ricardofrango.squadify.model.LeaderEntry
import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.model.TeamFormEntry
import com.ricardofrango.squadify.model.leaders
import com.ricardofrango.squadify.model.totals
import com.ricardofrango.squadify.presentation.ui.components.StatsCard
import com.ricardofrango.squadify.presentation.ui.components.StatsFilterSegments
import com.ricardofrango.squadify.presentation.ui.components.StatsHeaderRow
import com.ricardofrango.squadify.presentation.ui.components.StatsHeading
import com.ricardofrango.squadify.presentation.ui.components.StatsMessage
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.components.oneDecimal
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SuccessGreen
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.*
import kotlin.math.roundToInt

/**
 * The Season record card (design 31): W / D / L tiles, the played · for · against · GD line, and the
 * last five as tappable squares. A team belongs to one season, so the season pill is a label, not a
 * switcher. Errors and empty states stay inside the card; the rest of Team Details still loads.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TeamRecordCard(
    state: TeamRecordUiState,
    teamName: String,
    // "Shows up after the first finished match. Next up: …" and its View match button (32c); null
    // without a scheduled fixture.
    nextHint: String?,
    onViewNext: (() -> Unit)?,
    onRetry: () -> Unit,
    onFilterSelected: (StatsFilter) -> Unit,
    onOpenMatch: (String) -> Unit
) {
    val title = stringResource(Res.string.record_season_record)
    when (state) {
        TeamRecordUiState.Loading -> StatsCard(contentPadding = 16.dp) {
            StatsHeading(title)
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PitchGreen, modifier = Modifier.size(28.dp))
            }
        }

        TeamRecordUiState.Error -> StatsCard(contentPadding = 16.dp) {
            StatsHeading(title, Modifier.padding(bottom = 12.dp))
            StatsMessage(
                stringResource(Res.string.record_load_failed_title),
                stringResource(Res.string.record_load_failed_body),
                onRetry = onRetry
            )
        }

        is TeamRecordUiState.Loaded -> {
            val record = state.record
            if (record.matches.isEmpty()) {
                // No finished match yet: point at the next fixture (shown above) instead of zeros.
                StatsCard(contentPadding = 16.dp) {
                    StatsHeading(title, Modifier.padding(bottom = 12.dp))
                    StatsMessage(
                        stringResource(Res.string.record_no_results_title),
                        nextHint ?: stringResource(Res.string.record_no_results_body, teamName)
                    )
                    if (onViewNext != null) ViewMatchButton(onViewNext)
                }
                return
            }
            StatsCard {
                StatsHeaderRow(title, record.seasonName, onPillClick = null)
                StatsFilterSegments(state.filter, onFilterSelected)
                val t = record.totals(state.filter)
                if (t.played == 0) {
                    StatsMessage(
                        stringResource(state.filter.emptyStatsRes()),
                        stringResource(Res.string.stats_switch_to_all),
                        Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
                    )
                    return@StatsCard
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecordTile(t.won, stringResource(Res.string.record_won), SuccessGreen, Modifier.weight(1f))
                    RecordTile(t.drawn, stringResource(Res.string.record_drawn), DeepCarbon, Modifier.weight(1f))
                    RecordTile(t.lost, stringResource(Res.string.record_lost), AlertRed, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                val lineStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.record_played, t.played), style = lineStyle, color = DeepCarbon)
                    LabeledNumber(stringResource(Res.string.record_goals_for), t.goalsFor.toString())
                    LabeledNumber(stringResource(Res.string.record_goals_against), t.goalsAgainst.toString())
                    LabeledNumber(
                        stringResource(Res.string.record_goal_difference),
                        if (t.goalDifference > 0) "+${t.goalDifference}" else t.goalDifference.toString(),
                        valueColor = when {
                            t.goalDifference > 0 -> SuccessGreen
                            t.goalDifference < 0 -> AlertRed
                            else -> DeepCarbon
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(Res.string.record_form), style = MaterialTheme.typography.labelLarge, color = StatsSecondaryText)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        t.form.forEach { FormSquare(it) { onOpenMatch(it.matchId) } }
                    }
                    Text(stringResource(Res.string.record_latest_first), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal), color = StatsSecondaryText)
                }
                if (t.untypedMatches > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        pluralStringResource(Res.plurals.stats_untyped_note, t.untypedMatches, t.untypedMatches),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                        color = StatsSecondaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordTile(value: Int, label: String, color: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color(0xFFFAFAFA), border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.08f))) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, fontSize = 28.sp), color = color)
            Spacer(Modifier.height(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = StatsSecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LabeledNumber(label: String, value: String, valueColor: Color = DeepCarbon) {
    Text(
        buildAnnotatedString {
            append("$label ")
            withStyle(SpanStyle(fontWeight = FontWeight.Black, color = valueColor)) { append(value) }
        },
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = DeepCarbon
    )
}

@Composable
internal fun FormSquare(entry: TeamFormEntry, onClick: () -> Unit) {
    val (bg, fg, letter, word) = when (entry.result) {
        FormResult.WIN -> Quad(SuccessGreen, Color.White, Res.string.record_form_win, Res.string.record_result_win)
        FormResult.DRAW -> Quad(CarbonGray.copy(alpha = 0.12f), DeepCarbon, Res.string.record_form_draw, Res.string.record_result_draw)
        FormResult.LOSS -> Quad(AlertRed, Color.White, Res.string.record_form_loss, Res.string.record_result_loss)
    }
    // "Won 3–1 vs AD Fontelas" (31f).
    val description = stringResource(
        Res.string.record_form_a11y, stringResource(word), entry.match.ourScore, entry.match.opponentScore, entry.match.opponentName
    )
    Box(
        Modifier
            .size(26.dp)
            .background(bg, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(letter), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black), color = fg)
    }
}

private data class Quad(val bg: Color, val fg: Color, val letter: StringResource, val word: StringResource)

/**
 * Team leaders (design 31a): a two-column grid of top-three lists -- Goals, Assists, Minutes, and Avg
 * rating for staff only (members get three categories, no empty slot). Tapping a name opens the
 * player on this season and filter.
 */
@Composable
internal fun TeamLeadersCard(state: TeamRecordUiState.Loaded, onOpenPlayer: (String) -> Unit) {
    val leaders = state.record.leaders(state.filter)
    val categories = buildList {
        add(Triple(Res.string.record_leaders_goals, leaders.goals) { e: LeaderEntry -> e.value.roundToInt().toString() })
        add(Triple(Res.string.record_leaders_assists, leaders.assists) { e: LeaderEntry -> e.value.roundToInt().toString() })
        add(Triple(Res.string.record_leaders_minutes, leaders.minutes) { e: LeaderEntry -> "${e.value.roundToInt()}'" })
        if (state.record.canSeeRatings) add(Triple(Res.string.record_leaders_rating, leaders.averageRating) { e: LeaderEntry -> e.value.oneDecimal() })
    }
    StatsCard {
        StatsHeading(stringResource(Res.string.record_team_leaders), Modifier.padding(bottom = 10.dp))
        categories.chunked(2).forEachIndexed { i, pair ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { (title, entries, format) ->
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(title), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp), color = StatsSecondaryText)
                        Spacer(Modifier.height(2.dp))
                        if (entries.isEmpty()) {
                            Text(
                                if (title == Res.string.record_leaders_rating) stringResource(Res.string.record_leaders_not_enough_rated) else "—",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatsSecondaryText,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                        entries.forEach { e -> LeaderRow(e, format(e)) { onOpenPlayer(e.playerId) } }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LeaderRow(entry: LeaderEntry, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(entry.rank.toString(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp), color = StatsSecondaryText, modifier = Modifier.width(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            entry.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
            color = DeepCarbon,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(6.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black, fontSize = 13.5.sp), color = DeepCarbon, maxLines = 1)
    }
}

/** Whether there's anything for the leaders card: some stats line in the current filter. */
internal fun TeamRecordUiState.Loaded.hasLeaders(): Boolean {
    val l = record.leaders(filter)
    return l.goals.isNotEmpty() || l.assists.isNotEmpty() || l.minutes.isNotEmpty()
}
