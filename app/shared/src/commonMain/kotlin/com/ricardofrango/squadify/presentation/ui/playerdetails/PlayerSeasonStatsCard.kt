package com.ricardofrango.squadify.presentation.ui.playerdetails

import com.ricardofrango.squadify.presentation.ui.components.emptyStatsRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.PlayerSeason
import com.ricardofrango.squadify.model.PlayerSeasonMatch
import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.model.inFilter
import com.ricardofrango.squadify.model.totals
import com.ricardofrango.squadify.presentation.ui.components.MatchTypeTag
import com.ricardofrango.squadify.presentation.ui.components.RatingPill
import com.ricardofrango.squadify.presentation.ui.components.StatsCard
import com.ricardofrango.squadify.presentation.ui.components.StatsFilterSegments
import com.ricardofrango.squadify.presentation.ui.components.StatsHeaderRow
import com.ricardofrango.squadify.presentation.ui.components.StatsHeading
import com.ricardofrango.squadify.presentation.ui.components.StatsMessage
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.components.isTaggedInLists
import com.ricardofrango.squadify.presentation.ui.components.oneDecimal
import com.ricardofrango.squadify.presentation.ui.matchreport.MiniPill
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.Amber
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.DeepGreen
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SuccessGreen
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.*

private val DayMonth = LocalDate.Format { day(Padding.NONE); char(' '); monthName(MonthNames.ENGLISH_ABBREVIATED) }

/** "2026/27 · Sub-15 A" (two teams in one season: "Sub-15 A + Sub-15 B"). */
internal fun PlayerSeason.pillLabel(): String =
    if (teamNames.isEmpty()) seasonName else "$seasonName · ${teamNames.joinToString(" + ")}"

/**
 * The Season stats card (design 30): headline grid, secondary line and notes, or the card's own empty
 * / error / loading state. Staff see Avg rating as the fourth headline; players and parents see
 * Minutes in its slot (design 30b) -- their payload has no ratings at all.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerSeasonStatsCard(
    state: SeasonStatsUiState,
    playerFirstName: String,
    onRetry: () -> Unit,
    onOpenSeasons: () -> Unit,
    onFilterSelected: (StatsFilter) -> Unit
) {
    val title = stringResource(Res.string.stats_season_stats)
    when (state) {
        SeasonStatsUiState.Loading -> StatsCard(contentPadding = 16.dp) {
            StatsHeading(title)
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PitchGreen, modifier = Modifier.size(28.dp))
            }
        }

        SeasonStatsUiState.Error -> StatsCard(contentPadding = 16.dp) {
            StatsHeading(title, Modifier.padding(bottom = 12.dp))
            StatsMessage(
                stringResource(Res.string.stats_load_failed_title),
                stringResource(Res.string.stats_load_failed_body),
                onRetry = onRetry
            )
        }

        is SeasonStatsUiState.Loaded -> {
            val season = state.selectedSeason
            val everPlayed = state.stats.seasons.any { it.matches.isNotEmpty() || it.preStatsMatchCount > 0 }
            when {
                // A brand-new player: nothing to switch between yet, so no season pill (30c).
                season == null || !everPlayed -> StatsCard(contentPadding = 16.dp) {
                    StatsHeading(title, Modifier.padding(bottom = 12.dp))
                    Icon(painterResource(Res.drawable.ic_document), contentDescription = null, tint = Color(0xFFE0E0E0), modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(10.dp))
                    StatsMessage(
                        stringResource(Res.string.stats_no_matches_title),
                        stringResource(Res.string.stats_no_matches_body, playerFirstName)
                    )
                }

                // A season played only before stats were recorded: explain, rather than show zeros
                // that would read as "played and did nothing" (30c).
                season.matches.isEmpty() -> StatsCard(contentPadding = 16.dp) {
                    StatsHeaderRow(title, season.pillLabel(), onOpenSeasons)
                    if (season.preStatsMatchCount > 0) {
                        StatsMessage(
                            stringResource(Res.string.stats_pre_stats_title),
                            pluralStringResource(Res.plurals.stats_pre_stats_body, season.preStatsMatchCount, season.preStatsMatchCount, playerFirstName, season.seasonName)
                        )
                    } else {
                        StatsMessage(
                            stringResource(Res.string.stats_no_matches_title),
                            stringResource(Res.string.stats_no_matches_body, playerFirstName)
                        )
                    }
                }

                else -> StatsCard {
                    StatsHeaderRow(title, season.pillLabel(), onOpenSeasons)
                    StatsFilterSegments(state.filter, onFilterSelected)
                    val rows = season.matches.inFilter(state.filter)
                    if (rows.isEmpty()) {
                        StatsMessage(
                            stringResource(state.filter.emptyStatsRes()),
                            stringResource(Res.string.stats_switch_to_all),
                            Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
                        )
                    } else {
                        TotalsBlock(season.matches, state.filter, state.stats.canSeeRatings)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TotalsBlock(matches: List<PlayerSeasonMatch>, filter: StatsFilter, staff: Boolean) {
    val t = matches.totals(filter)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Headline(t.apps.toString(), stringResource(Res.string.stats_apps), Modifier.weight(1f))
        Headline(t.goals.toString(), stringResource(Res.string.stats_goals), Modifier.weight(1f), color = SuccessGreen)
        Headline(t.assists.toString(), stringResource(Res.string.stats_assists_short), Modifier.weight(1f))
        if (staff) {
            Column(Modifier.weight(1f)) {
                val avg = t.averageRating
                if (avg != null) {
                    RatingPill(avg, avg.oneDecimal(), minWidth = 44.dp, height = 34.dp, fontSize = 20.sp, corner = 10.dp)
                } else {
                    Text("—", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = DeepCarbon)
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.stats_avg_rating), style = MaterialTheme.typography.labelMedium, color = StatsSecondaryText)
            }
        } else {
            Headline(t.minutes.toString(), stringResource(Res.string.stats_minutes), Modifier.weight(1f))
        }
    }
    HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 10.dp), color = CarbonGray.copy(alpha = 0.08f))
    val lineStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.stats_started_of, t.starts, t.apps), style = lineStyle, color = DeepCarbon)
        if (staff) Text(stringResource(Res.string.stats_minutes_value, t.minutes), style = lineStyle, color = DeepCarbon)
        Text(stringResource(Res.string.stats_cards, t.yellowCards, t.redCards), style = lineStyle, color = DeepCarbon)
    }
    if (staff) {
        Spacer(Modifier.height(6.dp))
        Text(
            if (t.ratedMatches > 0) pluralStringResource(Res.plurals.stats_avg_from_rated, t.ratedMatches, t.ratedMatches)
            else stringResource(Res.string.stats_no_rated_matches),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
            color = StatsSecondaryText
        )
    }
    // Only under All -- inside a filter the line disappears rather than apologising (30a).
    if (t.untypedMatches > 0) {
        Spacer(Modifier.height(8.dp))
        Text(
            pluralStringResource(Res.plurals.stats_untyped_note, t.untypedMatches, t.untypedMatches),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
            color = StatsSecondaryText
        )
    }
}

@Composable
private fun Headline(value: String, label: String, modifier: Modifier, color: Color = DeepCarbon) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, fontSize = 28.sp), color = color, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = StatsSecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The per-match list under the totals, newest first; each row opens the match. */
@Composable
internal fun PlayerMatchesList(matches: List<PlayerSeasonMatch>, showRatings: Boolean, onOpenMatch: (String) -> Unit) {
    Column {
        StatsHeading(stringResource(Res.string.stats_matches), Modifier.padding(start = 2.dp, top = 4.dp, bottom = 8.dp))
        StatsCard(contentPadding = 0.dp) {
            matches.forEachIndexed { i, m ->
                PlayerMatchRow(m, showRatings) { onOpenMatch(m.matchId) }
                if (i < matches.lastIndex) HorizontalDivider(color = CarbonGray.copy(alpha = 0.07f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerMatchRow(m: PlayerSeasonMatch, showRating: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 8.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The date is centred in its column, so the space after it matches the card's edge before it.
        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
            Text(
                m.dateTime.toLocalDateTime(TimeZone.currentSystemDefault()).date.format(DayMonth),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                maxLines = 1,
                color = StatsSecondaryText
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            // "Sub-15 A vs AD Fontelas": which of the age group's teams he played for.
            Text(if (m.teamName.isNotEmpty()) "${m.teamName} vs ${m.opponentName}" else "vs ${m.opponentName}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = DeepCarbon)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                m.matchType?.takeIf(MatchType::isTaggedInLists)?.let { MatchTypeTag(it) }
                if (m.started) {
                    MiniPill(stringResource(Res.string.report_started), CarbonGray.copy(alpha = 0.08f), DeepCarbon)
                } else {
                    MiniPill(stringResource(Res.string.report_sub), Amber.copy(alpha = 0.2f), AmberText)
                }
                Text("${m.minutesPlayed}'", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = StatsSecondaryText)
                if (m.goals > 0) MiniPill(stringResource(Res.string.report_chip_goals, m.goals), SuccessGreen.copy(alpha = 0.12f), DeepGreen)
                if (m.assists > 0) MiniPill(stringResource(Res.string.report_chip_assists, m.assists), CarbonGray.copy(alpha = 0.07f), DeepCarbon)
                if (m.ownGoals > 0) MiniPill(stringResource(Res.string.report_chip_own_goals, m.ownGoals), AlertRed.copy(alpha = 0.1f), AlertRed)
                if (m.yellowCards > 0) MiniPill(if (m.yellowCards > 1) "×${m.yellowCards}" else "", CarbonGray.copy(alpha = 0.05f), DeepCarbon, swatch = Amber)
                if (m.redCards > 0) MiniPill("", CarbonGray.copy(alpha = 0.05f), DeepCarbon, swatch = AlertRed)
            }
        }
        if (showRating) {
            Spacer(Modifier.width(11.dp))
            val r = m.rating
            if (r != null) {
                RatingPill(r.toDouble(), r.toString(), minWidth = 36.dp, height = 32.dp, fontSize = 15.sp, corner = 9.dp)
            } else {
                Box(Modifier.size(width = 36.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                    Text("—", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black), color = StatsSecondaryText)
                }
            }
        }
    }
}

/**
 * Season picker (design 30a): every season he was registered in, newest first. A season he only
 * played before stats were recorded is greyed with an explanation, but can still be picked -- it
 * then explains itself instead of showing zeros.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeasonSheet(
    seasons: List<PlayerSeason>,
    selectedSeasonId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 36.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Text(stringResource(Res.string.stats_season), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, modifier = Modifier.weight(1f))
                Text(
                    stringResource(Res.string.stats_close),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = StatsSecondaryText,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                seasons.forEach { season ->
                    val selected = season.seasonId == selectedSeasonId
                    val preStatsOnly = season.matches.isEmpty() && season.preStatsMatchCount > 0
                    Surface(
                        onClick = { onSelect(season.seasonId) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) PitchGreen.copy(alpha = 0.07f) else Color.White,
                        border = BorderStroke(1.5.dp, if (selected) PitchGreen else CarbonGray.copy(alpha = if (preStatsOnly) 0.08f else 0.16f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text(
                                season.seasonName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 15.sp),
                                color = if (preStatsOnly) DeepCarbon.copy(alpha = 0.4f) else DeepCarbon
                            )
                            val sub = listOfNotNull(
                                season.teamNames.takeIf { it.isNotEmpty() }?.joinToString(" + "),
                                if (preStatsOnly) stringResource(Res.string.stats_pre_stats_option) else null
                            ).joinToString(" · ")
                            if (sub.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The rows the Matches list shows for the current season and filter. */
internal fun SeasonStatsUiState.Loaded.visibleMatches(): List<PlayerSeasonMatch> =
    selectedSeason?.matches?.inFilter(filter).orEmpty()
