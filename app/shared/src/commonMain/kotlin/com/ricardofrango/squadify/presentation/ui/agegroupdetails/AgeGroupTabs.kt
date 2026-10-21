package com.ricardofrango.squadify.presentation.ui.agegroupdetails

import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import squadify.app.shared.generated.resources.ic_close
import squadify.app.shared.generated.resources.btn_cancel
import squadify.app.shared.generated.resources.btn_remove
import squadify.app.shared.generated.resources.cd_remove_from_age_group
import squadify.app.shared.generated.resources.msg_remove_player_age_group
import squadify.app.shared.generated.resources.title_remove_player_age_group
import com.ricardofrango.squadify.model.Player
import androidx.compose.material3.Icon
import com.ricardofrango.squadify.presentation.ui.components.emptyStatsRes
import kotlinx.datetime.plus
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.model.*
import com.ricardofrango.squadify.presentation.ui.components.MatchRow
import com.ricardofrango.squadify.presentation.ui.components.RatingPill
import com.ricardofrango.squadify.presentation.ui.components.StatsCard
import com.ricardofrango.squadify.presentation.ui.components.StatsFilterSegments
import com.ricardofrango.squadify.presentation.ui.components.StatsHeading
import com.ricardofrango.squadify.presentation.ui.components.StatsMessage
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.components.labelRes
import com.ricardofrango.squadify.presentation.ui.components.oneDecimal
import com.ricardofrango.squadify.presentation.ui.teamdetails.FormSquare
import com.ricardofrango.squadify.presentation.ui.teamdetails.ViewMatchButton
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.Amber
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SuccessGreen
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import squadify.app.shared.generated.resources.msg_check_connection
import squadify.app.shared.generated.resources.msg_plans_load_failed
import com.ricardofrango.squadify.presentation.ui.training.SeasonPlanCard
import com.ricardofrango.squadify.presentation.ui.training.MonthPlanCard
import com.ricardofrango.squadify.presentation.ui.training.WeekPlanCard
import com.ricardofrango.squadify.presentation.ui.training.initialMonth
import com.ricardofrango.squadify.presentation.ui.training.initialWeek
import com.ricardofrango.squadify.presentation.ui.training.splitSessions
import com.ricardofrango.squadify.presentation.ui.training.chipFor
import com.ricardofrango.squadify.presentation.ui.training.SessionRow
import squadify.app.shared.generated.resources.label_past_sessions
import squadify.app.shared.generated.resources.label_upcoming_sessions
import squadify.app.shared.generated.resources.msg_no_sessions_body
import squadify.app.shared.generated.resources.msg_no_sessions_title
import squadify.app.shared.generated.resources.btn_create_sessions
import squadify.app.shared.generated.resources.label_sessions
import squadify.app.shared.generated.resources.msg_sessions_load_failed
import kotlinx.datetime.toLocalDateTime
import androidx.compose.foundation.lazy.LazyListScope
import com.ricardofrango.squadify.presentation.ui.training.SessionsCalendar
import squadify.app.shared.generated.resources.msg_no_sessions_day
import squadify.app.shared.generated.resources.btn_add_one_session
import squadify.app.shared.generated.resources.btn_calendar
import squadify.app.shared.generated.resources.btn_list
import com.ricardofrango.squadify.presentation.ui.training.AgeGroupTrainingStatsCard
import com.ricardofrango.squadify.model.AgeGroupTrainingStats

// The four tabs of Age Group Details (design 33). Every number is derived from AgeGroupDetails (teams,
// fixtures, staff, pool) and AgeGroupSeasonRecord (each team's season) by the shared code in
// SeasonStats.kt.

/** "A" for "Sub-15 A" -- the team's name without the age group's, for chips and compact labels. */
internal fun Team.shortLabel(ageGroupName: String): String =
    name.removePrefix(ageGroupName).trim().ifEmpty { name }

private const val RECENT_SHOWN = 3
private const val NAMES_SHOWN = 3

/** Heading with a count and an optional green "+ action" on the right ("TEAMS · 2   + Create team"). */
@Composable
private fun TabHeading(title: String, action: Pair<String, () -> Unit>?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        StatsHeading(title, Modifier.weight(1f))
        if (action != null) {
            Text(
                action.first,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                color = PitchGreen,
                modifier = Modifier.clickable(onClick = action.second)
            )
        }
    }
}

/** An empty state inside a card, with an optional outlined action. */
@Composable
private fun EmptyCard(title: String, body: String, action: Pair<String, () -> Unit>? = null) {
    StatsCard(contentPadding = 16.dp) {
        StatsMessage(title, body)
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            Surface(onClick = action.second, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.5.dp, CarbonGray.copy(alpha = 0.2f))) {
                Box(Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(action.first, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp), color = CarbonGray)
                }
            }
        }
    }
}

// ---- Stats ----

/**
 * Results by team with a combined line, and minutes across the pool (33a). Top scorers are left out
 * on purpose -- they're a team story, told in Team Details; here the question is "is everyone
 * getting minutes?".
 */
@Composable
internal fun AgeGroupStatsTab(
    details: AgeGroupDetails,
    recordState: AgeGroupRecordUiState,
    listState: LazyListState,
    padding: PaddingValues,
    modifier: Modifier,
    nextHint: String?,
    onViewNext: (() -> Unit)?,
    // Landscape (33e): results by team beside the minutes card.
    sideBySide: Boolean,
    onGoToTeams: () -> Unit,
    onRetry: () -> Unit,
    onFilterSelected: (StatsFilter) -> Unit,
    onOpenPlayer: (String) -> Unit,
    // Staff only (design 39j); null hides the card.
    trainingStats: AgeGroupTrainingStats? = null
) {
    LazyColumn(modifier, state = listState, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        statsItems(details, recordState, nextHint, onViewNext, sideBySide, onGoToTeams, onRetry, onFilterSelected, onOpenPlayer)
        trainingStats?.let { stats -> item(key = "training-stats") { AgeGroupTrainingStatsCard(stats, onOpenPlayer) } }
    }
}

private fun LazyListScope.statsItems(
    details: AgeGroupDetails,
    recordState: AgeGroupRecordUiState,
    nextHint: String?,
    onViewNext: (() -> Unit)?,
    sideBySide: Boolean,
    onGoToTeams: () -> Unit,
    onRetry: () -> Unit,
    onFilterSelected: (StatsFilter) -> Unit,
    onOpenPlayer: (String) -> Unit
) {
        when {
            details.teams.isEmpty() -> item {
                EmptyCard(
                    stringResource(Res.string.ag_no_teams_title),
                    stringResource(Res.string.ag_no_teams_body_stats),
                    stringResource(Res.string.ag_go_to_teams) to onGoToTeams
                )
            }
            recordState is AgeGroupRecordUiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PitchGreen, modifier = Modifier.size(28.dp))
                }
            }
            recordState is AgeGroupRecordUiState.Error -> item {
                StatsCard(contentPadding = 16.dp) {
                    StatsMessage(stringResource(Res.string.stats_load_failed_title), stringResource(Res.string.ag_stats_failed_body), onRetry = onRetry)
                }
            }
            recordState is AgeGroupRecordUiState.Loaded -> {
                val record = recordState.record
                if (record.teams.all { it.matches.isEmpty() }) {
                    // No finished match yet: point at the next fixture (33c).
                    item {
                        StatsCard(contentPadding = 16.dp) {
                            StatsMessage(stringResource(Res.string.record_no_results_title), nextHint ?: stringResource(Res.string.ag_no_results_body))
                            if (onViewNext != null) ViewMatchButton(onViewNext)
                        }
                    }
                } else {
                    item { StatsFilterSegments(recordState.filter, onFilterSelected, bottomSpacing = 0.dp) }
                    val combined = record.combined(recordState.filter)
                    if (combined.played == 0) {
                        item {
                            StatsCard(contentPadding = 16.dp) {
                                StatsMessage(
                                    stringResource(recordState.filter.emptyStatsRes()),
                                    stringResource(Res.string.stats_switch_to_all)
                                )
                            }
                        }
                    } else {
                        val teamsById = details.teams.associateBy { it.id }
                        if (sideBySide) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                                    Box(Modifier.weight(1f)) { ResultsByTeamCard(record, recordState.filter, combined) }
                                    Box(Modifier.weight(1f)) { PoolMinutesCard(record, recordState.filter, details.ageGroup.name, teamsById, onOpenPlayer) }
                                }
                            }
                        } else {
                            item { ResultsByTeamCard(record, recordState.filter, combined) }
                            item { PoolMinutesCard(record, recordState.filter, details.ageGroup.name, teamsById, onOpenPlayer) }
                        }
                    }
                }
            }
        }
}

@Composable
private fun ResultsByTeamCard(record: AgeGroupSeasonRecord, filter: StatsFilter, combined: TeamRecordTotals) {
    StatsCard(contentPadding = 0.dp) {
        StatsHeading(stringResource(Res.string.ag_results_by_team), Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp))
        record.teams.forEachIndexed { i, team ->
            if (i > 0) HorizontalDivider(color = CarbonGray.copy(alpha = 0.07f))
            val t = team.totals(filter)
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(team.teamName, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.5.sp), color = DeepCarbon, modifier = Modifier.weight(1f))
                    Text(stringResource(Res.string.ag_goals, t.goalsFor, t.goalsAgainst), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp), color = StatsSecondaryText)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WdlCount(t.won, stringResource(Res.string.record_form_win), SuccessGreen)
                    Spacer(Modifier.width(10.dp))
                    WdlCount(t.drawn, stringResource(Res.string.record_form_draw), DeepCarbon)
                    Spacer(Modifier.width(10.dp))
                    WdlCount(t.lost, stringResource(Res.string.record_form_loss), AlertRed)
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { t.form.forEach { FormSquare(it) {} } }
                }
            }
        }
        // The age group as a whole, in a quiet footer row.
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFFAFAFA)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(Res.string.ag_combined, combined.played), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp), color = StatsSecondaryText, modifier = Modifier.weight(1f))
            Text(
                "${combined.won} ${stringResource(Res.string.record_form_win)} · ${combined.drawn} ${stringResource(Res.string.record_form_draw)} · ${combined.lost} ${stringResource(Res.string.record_form_loss)} · ${combined.goalsFor}–${combined.goalsAgainst}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.5.sp),
                color = DeepCarbon
            )
        }
    }
}

@Composable
private fun WdlCount(value: Int, letter: String, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, fontSize = 20.sp), color = color)
        Spacer(Modifier.width(3.dp))
        Text(letter, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = StatsSecondaryText, modifier = Modifier.padding(bottom = 3.dp))
    }
}

private val BandColors = mapOf(
    MinutesBand.LOTS to SuccessGreen,
    MinutesBand.SOME to SuccessGreen.copy(alpha = 0.5f),
    MinutesBand.LITTLE to Amber,
    MinutesBand.NONE to CarbonGray.copy(alpha = 0.22f)
)

/**
 * Minutes across the pool, in four neutrally-named bands so it reads as a plan, not a verdict (33a).
 * Only the two lower bands list names -- those are the ones a coordinator acts on. Tapping a band
 * shows everyone in it; a name opens the player.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PoolMinutesCard(
    record: AgeGroupSeasonRecord,
    filter: StatsFilter,
    ageGroupName: String,
    teamsById: Map<String, Team>,
    onOpenPlayer: (String) -> Unit
) {
    val pool = record.poolMinutes(filter)
    var openBand by remember { mutableStateOf<MinutesBand?>(null) }
    StatsCard(contentPadding = 0.dp) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            StatsHeading(stringResource(Res.string.ag_minutes_title), Modifier.weight(1f))
            Text(stringResource(Res.string.ag_pool_players, pool.totalPlayers), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = StatsSecondaryText)
        }
        // Stacked bar of the four bands.
        if (pool.totalPlayers > 0) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).height(10.dp).clip(RoundedCornerShape(5.dp))) {
                MinutesBand.entries.forEach { band ->
                    val count = pool.bands.getValue(band).size
                    if (count > 0) Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(BandColors.getValue(band)))
                }
            }
        }
        MinutesBand.entries.forEach { band ->
            val players = pool.bands.getValue(band)
            HorizontalDivider(color = CarbonGray.copy(alpha = 0.07f))
            Column(Modifier.fillMaxWidth().clickable(enabled = players.isNotEmpty()) { openBand = if (openBand == band) null else band }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(BandColors.getValue(band), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${stringResource(band.labelRes())} · ${band.range()}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                        color = DeepCarbon,
                        modifier = Modifier.weight(1f)
                    )
                    Text(players.size.toString(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black), color = DeepCarbon)
                    if (players.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            painterResource(Res.drawable.ic_expand_more), contentDescription = null, tint = Color(0xFF9E9E9E),
                            modifier = Modifier.size(16.dp).rotate(if (openBand == band) 180f else 0f)
                        )
                    }
                }
                val namesListed = band == MinutesBand.LITTLE || band == MinutesBand.NONE
                if (openBand == band) {
                    Spacer(Modifier.height(8.dp))
                    // Plain clickable chips: a Surface(onClick) is padded up to the 48dp minimum touch
                    // target, which left a big gap between each row of names.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        players.forEach { p ->
                            Text(
                                p.name,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CarbonGray.copy(alpha = 0.06f))
                                    .clickable { onOpenPlayer(p.id) }
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                                color = DeepCarbon
                            )
                        }
                    }
                } else if (namesListed && players.isNotEmpty()) {
                    val shown = players.take(NAMES_SHOWN).joinToString(", ") { it.name }
                    val more = players.size - NAMES_SHOWN
                    Text(
                        if (more > 0) "$shown, +$more" else shown,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = StatsSecondaryText,
                        modifier = Modifier.padding(start = 18.dp, top = 3.dp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (record.teams.size > 1) {
            HorizontalDivider(color = CarbonGray.copy(alpha = 0.07f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                val label = if (record.teams.size == 2) {
                    val (a, b) = record.teams.map { teamsById[it.teamId]?.shortLabel(ageGroupName) ?: it.teamName }
                    stringResource(Res.string.ag_played_both, a, b)
                } else stringResource(Res.string.ag_played_several)
                Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = StatsSecondaryText, modifier = Modifier.weight(1f))
                Text(pool.playedForSeveralTeams.toString(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black), color = DeepCarbon)
            }
        }
        Text(
            stringResource(Res.string.ag_band_hint),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
            color = StatsSecondaryText,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 2.dp)
        )
    }
}

private fun MinutesBand.labelRes() = when (this) {
    MinutesBand.LOTS -> Res.string.ag_band_lots
    MinutesBand.SOME -> Res.string.ag_band_some
    MinutesBand.LITTLE -> Res.string.ag_band_little
    MinutesBand.NONE -> Res.string.ag_band_none
}

// Built in code: the resource generator doesn't reliably unescape \' in strings (see MatchRow).
private fun MinutesBand.range() = when (this) {
    MinutesBand.LOTS -> "300'+"
    MinutesBand.SOME -> "100–299'"
    MinutesBand.LITTLE -> "1–99'"
    MinutesBand.NONE -> "0 min"
}

// ---- Teams ----

/** Each team with its season (All matches), players used, avg rating for staff, and its next fixture. */
@Composable
internal fun AgeGroupTeamsTab(
    details: AgeGroupDetails,
    recordState: AgeGroupRecordUiState,
    listState: LazyListState,
    padding: PaddingValues,
    modifier: Modifier,
    nextLabel: (Match) -> String,
    onCreateTeam: () -> Unit,
    onOpenTeam: (String) -> Unit
) {
    val records = (recordState as? AgeGroupRecordUiState.Loaded)?.record?.teams?.associateBy { it.teamId }.orEmpty()
    val staff = (recordState as? AgeGroupRecordUiState.Loaded)?.record?.canSeeRatings == true
    LazyColumn(modifier, state = listState, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TabHeading(
                stringResource(Res.string.team_heading_count, stringResource(Res.string.tab_teams), details.teams.size),
                if (details.canCreateTeam) stringResource(Res.string.ag_create_team_short) to onCreateTeam else null
            )
        }
        if (details.teams.isEmpty()) {
            item {
                EmptyCard(
                    stringResource(Res.string.ag_no_teams_title),
                    stringResource(Res.string.ag_no_teams_body_teams),
                    if (details.canCreateTeam) stringResource(Res.string.label_create_team) to onCreateTeam else null
                )
            }
        } else {
            items(details.teams.sortedBy { it.name }, key = { it.id }) { team ->
                val record = records[team.id]
                val next = details.upcomingMatches.firstOrNull { it.teamId == team.id && it.status == MatchStatus.SCHEDULED }
                TeamCard(team, record, staff, next?.let(nextLabel)) { onOpenTeam(team.id) }
            }
        }
    }
}

@Composable
private fun TeamCard(team: Team, record: TeamSeasonRecord?, staff: Boolean, nextLabel: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.12f))) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(team.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp), color = DeepCarbon)
                    if (record != null) {
                        Text(stringResource(Res.string.ag_players_used, record.playersUsed()), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = StatsSecondaryText)
                    }
                }
                val avg = record?.averageRating()
                if (staff && avg != null) RatingPill(avg, avg.oneDecimal(), minWidth = 40.dp, height = 32.dp, fontSize = 14.5.sp, corner = 9.dp)
            }
            if (record != null && record.matches.isNotEmpty()) {
                val t = record.totals(StatsFilter.ALL)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WdlCount(t.won, stringResource(Res.string.record_form_win), SuccessGreen)
                    Spacer(Modifier.width(10.dp))
                    WdlCount(t.drawn, stringResource(Res.string.record_form_draw), DeepCarbon)
                    Spacer(Modifier.width(10.dp))
                    WdlCount(t.lost, stringResource(Res.string.record_form_loss), AlertRed)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(Res.string.ag_goals, t.goalsFor, t.goalsAgainst), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp), color = StatsSecondaryText)
                }
            }
            if (nextLabel != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().background(CarbonGray.copy(alpha = 0.05f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(Res.string.label_next).uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp), color = PitchGreen)
                    Spacer(Modifier.width(8.dp))
                    Text(nextLabel, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp), color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---- Matches ----

/** Upcoming and recent results of every team, with a team filter when there are 2+ teams (33f). */
@Composable
internal fun AgeGroupMatchesTab(
    details: AgeGroupDetails,
    teamFilter: String?,
    listState: LazyListState,
    padding: PaddingValues,
    modifier: Modifier,
    onTeamFilter: (String?) -> Unit,
    onSchedule: () -> Unit,
    onOpenMatch: (String) -> Unit
) {
    val opponentsById = remember(details.opponents) { details.opponents.associateBy { it.id } }
    val teamsById = remember(details.teams) { details.teams.associateBy { it.id } }
    var showAllResults by remember { mutableStateOf(false) }
    val upcoming = details.upcomingMatches.filter { teamFilter == null || it.teamId == teamFilter }
    val results = details.recentResults.filter { teamFilter == null || it.match.teamId == teamFilter }
    fun opponent(id: String) = opponentsById[id]?.let { it.shortName ?: it.name } ?: "—"

    LazyColumn(modifier, state = listState, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (details.teams.isEmpty()) {
            item { EmptyCard(stringResource(Res.string.msg_no_upcoming_matches), stringResource(Res.string.ag_no_matches_no_teams)) }
            return@LazyColumn
        }
        if (details.teams.size > 1) {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChipPill(stringResource(Res.string.ag_all_teams), teamFilter == null) { onTeamFilter(null) }
                    details.teams.sortedBy { it.name }.forEach { team ->
                        FilterChipPill(team.name, teamFilter == team.id) { onTeamFilter(team.id) }
                    }
                }
            }
        }
        item {
            TabHeading(
                stringResource(Res.string.team_heading_count, stringResource(Res.string.label_upcoming), upcoming.size),
                if (details.canManageMatch) stringResource(Res.string.team_schedule_short) to onSchedule else null
            )
        }
        if (upcoming.isEmpty()) {
            item { StatsCard(contentPadding = 16.dp) { Text(stringResource(Res.string.msg_no_upcoming_matches), style = MaterialTheme.typography.bodyMedium, color = StatsSecondaryText) } }
        } else {
            items(upcoming, key = { it.id }) { match ->
                MatchRow(
                    match = match,
                    opponentName = opponent(match.opponentId),
                    teamName = teamsById[match.teamId]?.name,
                    score = details.upcomingMatchScores[match.id],
                    onClick = { onOpenMatch(match.id) }
                )
            }
        }
        item {
            TabHeading(stringResource(Res.string.team_heading_count, stringResource(Res.string.label_recent_results), results.size), null)
        }
        if (results.isEmpty()) {
            item { StatsCard(contentPadding = 16.dp) { Text(stringResource(Res.string.msg_no_results_yet), style = MaterialTheme.typography.bodyMedium, color = StatsSecondaryText) } }
        } else {
            val shown = if (showAllResults) results else results.take(RECENT_SHOWN)
            items(shown, key = { it.match.id }) { r ->
                MatchRow(
                    match = r.match,
                    opponentName = opponent(r.match.opponentId),
                    teamName = teamsById[r.match.teamId]?.name,
                    score = MatchScore(r.match.id, r.ourScore, r.opponentScore),
                    onClick = { onOpenMatch(r.match.id) }
                )
            }
            if (!showAllResults && results.size > RECENT_SHOWN) {
                item {
                    Text(
                        stringResource(Res.string.team_see_all, results.size),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                        color = PitchGreen,
                        modifier = Modifier.clickable { showAllResults = true }.padding(horizontal = 2.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) ClubChrome else Color.White,
        border = BorderStroke(1.5.dp, if (selected) ClubChrome else CarbonGray.copy(alpha = 0.16f))
    ) {
        Box(Modifier.heightIn(min = 32.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 12.5.sp), color = if (selected) Color.White else DeepCarbon)
        }
    }
}

// ---- Members ----

/**
 * Staff first (short, and who to contact), then the pool with search (33f). Player rows show this
 * season's apps · minutes and a chip per team they played for.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AgeGroupMembersTab(
    details: AgeGroupDetails,
    recordState: AgeGroupRecordUiState,
    searchQuery: String,
    listState: LazyListState,
    padding: PaddingValues,
    modifier: Modifier,
    onSearchChange: (String) -> Unit,
    onAddStaff: () -> Unit,
    onAddPlayers: () -> Unit,
    onOpenPlayer: (String) -> Unit,
    // Null for readers who can't open Member details (below coordinator): the rows stay inert.
    onOpenStaff: ((String) -> Unit)? = null,
    // Null without ASSIGN_PLAYERS_TO_AGE_GROUP: no ✕ on the player rows.
    onRemovePlayer: ((String) -> Unit)? = null
) {
    var confirmRemove by remember { mutableStateOf<Player?>(null) }
    val members = (recordState as? AgeGroupRecordUiState.Loaded)?.record?.memberSeasons()
    val teamsById = remember(details.teams) { details.teams.associateBy { it.id } }
    val players = remember(details.players, searchQuery) {
        val q = searchQuery.trim().lowercase()
        details.players
            .filter { q.isEmpty() || "${it.firstName} ${it.lastName}".lowercase().contains(q) }
            // Position order (Goalkeeper -> Striker), no position last, then name.
            .sortedWith(compareBy({ it.position?.ordinal ?: Int.MAX_VALUE }, { it.firstName.lowercase() }, { it.lastName.lowercase() }))
    }
    // Disabled players stay in the pool but get their own section below the active ones.
    val (activePlayers, disabledPlayers) = players.partition { it.active }
    val disabledCount = details.players.count { !it.active }

    LazyColumn(modifier, state = listState, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ---- Staff ----
        item {
            TabHeading(
                stringResource(Res.string.team_heading_count, stringResource(Res.string.ag_staff_heading), details.staff.size),
                if (details.canAddStaff) stringResource(Res.string.ag_add_staff_short) to onAddStaff else null
            )
        }
        if (details.staff.isEmpty()) {
            item {
                EmptyCard(
                    stringResource(Res.string.ag_no_staff_title),
                    stringResource(Res.string.ag_no_staff_body),
                    if (details.canAddStaff) stringResource(Res.string.btn_add_staff) to onAddStaff else null
                )
            }
        } else {
            item {
                StatsCard(contentPadding = 0.dp) {
                    details.staff.forEachIndexed { i, member ->
                        if (i > 0) HorizontalDivider(color = CarbonGray.copy(alpha = 0.07f))
                        Row(
                            Modifier.fillMaxWidth()
                                .then(if (onOpenStaff != null) Modifier.clickable { onOpenStaff(member.userId) } else Modifier)
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(36.dp).background(CarbonGray.copy(alpha = 0.08f), CircleShape), contentAlignment = Alignment.Center) {
                                Text("${member.firstName.take(1)}${member.lastName.take(1)}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${member.firstName} ${member.lastName}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.5.sp), color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(if (member.role == TeamRole.COACH) Res.string.label_role_coach else Res.string.label_role_staff),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = StatsSecondaryText
                                )
                            }
                            if (onOpenStaff != null) {
                                Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null, tint = DeepCarbon.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

        // ---- Player pool ----
        item {
            TabHeading(
                stringResource(Res.string.team_heading_count, stringResource(Res.string.ag_pool_heading), details.players.size - disabledCount),
                if (details.canAssignPlayersToAgeGroup) stringResource(Res.string.ag_add_players_short) to onAddPlayers else null
            )
        }
        if (details.players.isEmpty()) {
            item {
                EmptyCard(
                    stringResource(Res.string.ag_no_players_title),
                    stringResource(Res.string.ag_no_players_body),
                    if (details.canAssignPlayersToAgeGroup) stringResource(Res.string.btn_add_players) to onAddPlayers else null
                )
            }
        } else {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text(stringResource(Res.string.label_search_players)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DeepCarbon, unfocusedTextColor = DeepCarbon,
                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                        focusedBorderColor = PitchGreen, unfocusedBorderColor = CarbonGray.copy(alpha = 0.2f)
                    )
                )
            }
            @Composable
            fun Rows(list: List<Player>) {
                StatsCard(contentPadding = 0.dp) {
                    list.forEachIndexed { i, player ->
                        if (i > 0) HorizontalDivider(color = CarbonGray.copy(alpha = 0.07f))
                        MemberRow(
                            player = player,
                            season = members?.get(player.id),
                            seasonKnown = members != null,
                            playsUp = player.birthDate.year > details.ageGroup.endYear,
                            teamNames = members?.get(player.id)?.teamIds.orEmpty().mapNotNull { teamsById[it]?.name }.sorted(),
                            onClick = { onOpenPlayer(player.id) },
                            onRemove = if (onRemovePlayer != null) { { confirmRemove = player } } else null
                        )
                    }
                }
            }
            if (activePlayers.isNotEmpty()) item(key = "pool-active") { Rows(activePlayers) }
            if (disabledPlayers.isNotEmpty()) {
                item(key = "pool-disabled") {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(Res.drawable.ic_block), contentDescription = null, tint = AlertRed, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            StatsHeading(stringResource(Res.string.team_heading_count, stringResource(Res.string.ag_disabled_heading), disabledPlayers.size))
                        }
                        Text(stringResource(Res.string.msg_ag_disabled_hint), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = StatsSecondaryText, modifier = Modifier.padding(horizontal = 2.dp))
                        Rows(disabledPlayers)
                    }
                }
            }
        }
    }
    confirmRemove?.let { player ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(Res.string.title_remove_player_age_group, "${player.firstName} ${player.lastName}", details.ageGroup.name)) },
            text = { Text(stringResource(Res.string.msg_remove_player_age_group)) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = null; onRemovePlayer?.invoke(player.id) }) {
                    Text(stringResource(Res.string.btn_remove), color = AlertRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) {
                    Text(stringResource(Res.string.btn_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

/**
 * A pool player: shirt number (centred in its column), name, position + this season's apps · minutes,
 * then a full-name tag for each team he played for ("Sub-15 A"), so A and B are never ambiguous.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberRow(player: Player, season: MemberSeason?, seasonKnown: Boolean, playsUp: Boolean, teamNames: List<String>, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    // A disabled player (own section, see AgeGroupMembersTab): faded, with a red "Disabled" chip. The ✕ stays fully visible.
    val faded = Modifier.alpha(if (player.active) 1f else 0.45f)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 10.dp, end = 12.dp, top = 11.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(32.dp).then(faded), contentAlignment = Alignment.Center) {
            Text(
                player.shirtNumber?.toString() ?: "—",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 16.sp),
                color = if (player.shirtNumber != null) PitchGreen else DeepCarbon.copy(alpha = 0.35f)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${player.firstName} ${player.lastName}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.5.sp), color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).then(faded))
                if (!player.active) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.label_player_disabled),
                        modifier = Modifier.background(AlertRed.copy(alpha = 0.1f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
                        color = AlertRed,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            FlowRow(faded, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(
                    player.position?.let { stringResource(it.labelRes()) } ?: stringResource(Res.string.report_no_position),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp), color = StatsSecondaryText
                )
                if (seasonKnown) {
                    Text(
                        // The minutes' apostrophe is appended here (resource generator quirk, see MatchRow).
                        if (season != null) "${pluralStringResource(Res.plurals.ag_member_apps, season.apps, season.apps)} · ${season.minutes}'"
                        else stringResource(Res.string.ag_no_apps),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                        color = if (season != null) DeepCarbon else StatsSecondaryText
                    )
                }
                if (playsUp) {
                    Surface(shape = RoundedCornerShape(6.dp), color = PitchGreen.copy(alpha = 0.12f)) {
                        Text(stringResource(Res.string.label_plays_up), modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp), color = PitchGreen)
                    }
                }
            }
            if (teamNames.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                FlowRow(faded, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    teamNames.forEach { name ->
                        Text(
                            name,
                            modifier = Modifier.background(CarbonGray.copy(alpha = 0.07f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
                            color = DeepCarbon,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        if (onRemove != null) {
            // Out of the age group only (the confirmation says so); compact, like the plan editors' ✕.
            IconButton(onClick = onRemove, modifier = Modifier.padding(start = 4.dp).size(32.dp)) {
                Icon(
                    painterResource(Res.drawable.ic_close),
                    stringResource(Res.string.cd_remove_from_age_group, "${player.firstName} ${player.lastName}"),
                    tint = DeepCarbon.copy(alpha = 0.45f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Training tab (design 40a/b): Season plan → This month → This week, each opening its own screen.
 * The month and week cards open on today's and move with ‹ › within the season; back on the tab they
 * start from today again. Players and parents don't see plans ([AgeGroupPlansUiState.Hidden]).
 */
@Composable
internal fun AgeGroupTrainingTab(
    plansState: AgeGroupPlansUiState,
    sessionsState: AgeGroupSessionsUiState,
    poolSize: Int,
    onRetrySessions: () -> Unit,
    onCreateSessions: () -> Unit,
    onAddSession: (date: LocalDate?) -> Unit,
    onOpenSession: (sessionId: String) -> Unit,
    listState: LazyListState,
    padding: PaddingValues,
    modifier: Modifier,
    onRetry: () -> Unit,
    onOpenSeason: () -> Unit,
    onEditSeason: () -> Unit,
    onOpenMonth: (LocalDate) -> Unit,
    onEditMonth: (LocalDate) -> Unit,
    onOpenWeek: (LocalDate) -> Unit,
    onEditWeek: (LocalDate) -> Unit
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    // List or Calendar, and the calendar's month and selected day; they reset when the tab is left.
    var calendar by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(LocalDate(today.year, today.month, 1)) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(today) }
    val view = SessionsView(calendar, month, selectedDay, { calendar = it }, { month = it }, { selectedDay = it })
    LazyColumn(modifier, state = listState, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (plansState) {
            AgeGroupPlansUiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PitchGreen) }
            }
            AgeGroupPlansUiState.Error -> item {
                StatsCard { StatsMessage(stringResource(Res.string.msg_plans_load_failed), stringResource(Res.string.msg_check_connection), onRetry = onRetry) }
            }
            AgeGroupPlansUiState.Hidden -> Unit
            is AgeGroupPlansUiState.Loaded -> {
                val plans = plansState.plans
                item(key = "season") { SeasonPlanCard(plans, today, onOpenSeason, onEditSeason) }
                item(key = "month") {
                    var month by remember(plans.season.id) { mutableStateOf(initialMonth(plans, today)) }
                    month?.let { m -> MonthPlanCard(plans, m, onMove = { month = it }, onOpen = { onOpenMonth(m) }, onEdit = { onEditMonth(m) }) }
                }
                item(key = "week") {
                    var week by remember(plans.season.id) { mutableStateOf(initialWeek(plans, today)) }
                    val weekSessions = (sessionsState as? AgeGroupSessionsUiState.Loaded)?.sessions?.sessions.orEmpty()
                    week?.let { w ->
                        val sunday = w.plus(kotlinx.datetime.DatePeriod(days = 6))
                        WeekPlanCard(
                            plans, w, onMove = { week = it }, onOpen = { onOpenWeek(w) }, onEdit = { onEditWeek(w) },
                            sessions = weekSessions.filter { it.date in w..sunday }, onOpenSession = onOpenSession
                        )
                    }
                }
            }
        }
        sessionItems(sessionsState, poolSize, view, onRetrySessions, onCreateSessions, onAddSession, onOpenSession)
    }
}

/** The sessions under the plan cards (design 39a/b): Upcoming, then Past, or the empty state. */
/** The sessions' List / Calendar choice, hoisted out of the lazy list. */
private class SessionsView(
    val calendar: Boolean,
    val month: LocalDate,
    val selectedDay: LocalDate?,
    val onCalendar: (Boolean) -> Unit,
    val onMonth: (LocalDate) -> Unit,
    val onSelectDay: (LocalDate) -> Unit
)

private fun LazyListScope.sessionItems(
    state: AgeGroupSessionsUiState,
    poolSize: Int,
    view: SessionsView,
    onRetry: () -> Unit,
    onCreateSessions: () -> Unit,
    onAddSession: (LocalDate?) -> Unit,
    onOpenSession: (String) -> Unit
) {
    when (state) {
        AgeGroupSessionsUiState.Loading -> Unit
        AgeGroupSessionsUiState.Error -> item {
            StatsCard { StatsMessage(stringResource(Res.string.msg_sessions_load_failed), stringResource(Res.string.msg_check_connection), onRetry = onRetry) }
        }
        is AgeGroupSessionsUiState.Loaded -> {
            val data = state.sessions
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            item(key = "sessions-header") {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatsHeading(stringResource(Res.string.label_sessions), Modifier.weight(1f))
                        if (data.sessions.isNotEmpty()) {
                            FilterChipPill(stringResource(Res.string.btn_list), !view.calendar) { view.onCalendar(false) }
                            Spacer(Modifier.width(6.dp))
                            FilterChipPill(stringResource(Res.string.btn_calendar), view.calendar) { view.onCalendar(true) }
                        }
                    }
                    if (data.canEdit && data.sessions.isNotEmpty()) {
                        Row {
                            TextButton(onClick = onCreateSessions) { Text(stringResource(Res.string.btn_create_sessions), color = PitchGreen, fontWeight = FontWeight.Bold) }
                            TextButton(onClick = { onAddSession(if (view.calendar) view.selectedDay else null) }) { Text(stringResource(Res.string.btn_add_one_session), color = PitchGreen, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            if (data.sessions.isEmpty()) {
                item(key = "sessions-empty") {
                    StatsCard {
                        StatsMessage(stringResource(Res.string.msg_no_sessions_title), stringResource(Res.string.msg_no_sessions_body))
                        if (data.canEdit) {
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onCreateSessions, colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White)) {
                                Text(stringResource(Res.string.btn_create_sessions), fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { onAddSession(null) }) { Text(stringResource(Res.string.btn_add_one_session), color = PitchGreen, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                return
            }
            if (view.calendar) {
                // The season's months, widened to any sessions outside them (clubs train past the season's end).
                val season = data.season
                val firstMonth = minOf(season.startDate, data.sessions.minOfOrNull { it.date } ?: season.startDate).let { LocalDate(it.year, it.month, 1) }
                val lastMonth = maxOf(season.endDate, data.sessions.maxOfOrNull { it.date } ?: season.endDate).let { LocalDate(it.year, it.month, 1) }
                item(key = "calendar") {
                    SessionsCalendar(
                        month = view.month, sessions = data.sessions, country = data.country, selected = view.selectedDay, now = now,
                        poolSize = poolSize, canRate = data.canRate,
                        canGoBack = view.month > firstMonth,
                        canGoForward = view.month < lastMonth,
                        onMonthChange = view.onMonth, onSelect = view.onSelectDay
                    )
                }
                val dayNext = splitSessions(data.sessions, now).first.let { up -> if (up.any { it.date == now.date }) null else up.firstOrNull { !it.cancelled } }
                val ofDay = data.sessions.filter { it.date == view.selectedDay }.sortedBy { it.startTime }
                if (ofDay.isEmpty()) {
                    item(key = "day-empty") { Text(stringResource(Res.string.msg_no_sessions_day), style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.5f), modifier = Modifier.padding(8.dp)) }
                } else {
                    items(ofDay, key = { "day-${it.id}" }) { session ->
                        SessionRow(session, chipFor(session, now, dayNext, poolSize, data.canRate)) { onOpenSession(session.id) }
                    }
                }
                return
            }
            val (upcoming, past) = splitSessions(data.sessions, now)
            val next = if (upcoming.any { it.date == now.date }) null else upcoming.firstOrNull { !it.cancelled }
            if (upcoming.isNotEmpty()) {
                item(key = "upcoming") { StatsHeading(stringResource(Res.string.label_upcoming_sessions)) }
                items(upcoming, key = { it.id }) { session ->
                    SessionRow(session, chipFor(session, now, next, poolSize, data.canRate)) { onOpenSession(session.id) }
                }
            }
            if (past.isNotEmpty()) {
                item(key = "past") { StatsHeading(stringResource(Res.string.label_past_sessions), Modifier.padding(top = 8.dp)) }
                items(past, key = { it.id }) { session ->
                    SessionRow(session, chipFor(session, now, next, poolSize, data.canRate)) { onOpenSession(session.id) }
                }
            }
        }
    }
}
