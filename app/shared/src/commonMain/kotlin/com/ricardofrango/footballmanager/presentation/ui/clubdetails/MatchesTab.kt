package com.ricardofrango.footballmanager.presentation.ui.clubdetails

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ricardofrango.footballmanager.model.Match
import com.ricardofrango.footballmanager.model.MatchStatus
import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.presentation.ui.theme.CarbonGray
import com.ricardofrango.footballmanager.presentation.ui.theme.DeepCarbon
import com.ricardofrango.footballmanager.presentation.ui.theme.PitchGreen
import footballmanager.app.shared.generated.resources.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

private val CardShape = RoundedCornerShape(16.dp)
private val LiveRed = Color(0xFFC0392B)

/**
 * The club Matches tab (design 14A / 14B): the club-wide fixture list, so each row names its team.
 * Season / age-group / team filter pills + an Upcoming / Finished / All status control, then the
 * matches grouped into date-relative buckets. A match in progress renders as a green-bordered card
 * with a LIVE badge and running minute in place of the date.
 */
@Composable
fun MatchesTab(
    state: ClubDetailsUiState.Idle,
    viewModel: ClubDetailsViewModel,
    onGoToAgeGroups: () -> Unit
) {
    val details = state.details ?: return

    // The club has no fixtures at all -- the full empty state, no filter row.
    if (state.matches.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.White).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_calendar),
                contentDescription = null,
                tint = DeepCarbon.copy(alpha = 0.3f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.msg_no_matches_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.msg_no_matches_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = DeepCarbon.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onGoToAgeGroups) {
                Text(stringResource(Res.string.btn_go_to_age_groups))
            }
        }
        return
    }

    val teamsById = remember(details.teams) { details.teams.associateBy { it.id } }
    val opponentsById = remember(state.opponents) { state.opponents.associateBy { it.id } }
    val ageGroupsForSeason = remember(details.ageGroups, state.selectedMatchesSeasonId) {
        details.ageGroups.filter { state.selectedMatchesSeasonId == null || it.seasonId == state.selectedMatchesSeasonId }
    }
    val teamsForFilter = remember(details.teams, state.selectedMatchesSeasonId, state.selectedMatchesAgeGroupId) {
        details.teams.filter {
            (state.selectedMatchesSeasonId == null || it.seasonId == state.selectedMatchesSeasonId) &&
                (state.selectedMatchesAgeGroupId == null || it.ageGroupId == state.selectedMatchesAgeGroupId)
        }
    }

    val groups = remember(state.matches, state.selectedMatchesSeasonId, state.selectedMatchesAgeGroupId, state.selectedMatchesTeamId, state.matchesStatus, teamsById) {
        buildGroups(state, teamsById)
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                FilterSelector(
                    selectedOption = details.seasons.find { it.id == state.selectedMatchesSeasonId }?.name
                        ?: stringResource(Res.string.label_all_seasons),
                    options = listOf(null to stringResource(Res.string.label_all_seasons)) +
                        details.seasons.map { it.id to it.name },
                    onOptionSelected = viewModel::onMatchesSeasonSelected
                )
            }
            item {
                FilterSelector(
                    selectedOption = ageGroupsForSeason.find { it.id == state.selectedMatchesAgeGroupId }?.name
                        ?: stringResource(Res.string.label_all_ages),
                    options = listOf(null to stringResource(Res.string.label_all_ages)) +
                        ageGroupsForSeason.map { it.id to it.name },
                    onOptionSelected = viewModel::onMatchesAgeGroupSelected
                )
            }
            item {
                FilterSelector(
                    selectedOption = teamsForFilter.find { it.id == state.selectedMatchesTeamId }?.name
                        ?: stringResource(Res.string.label_all_teams),
                    options = listOf(null to stringResource(Res.string.label_all_teams)) +
                        teamsForFilter.map { it.id to it.name },
                    onOptionSelected = viewModel::onMatchesTeamSelected
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusChip(Res.string.matches_status_upcoming, state.matchesStatus == MatchesStatusFilter.UPCOMING) {
                viewModel.onMatchesStatusSelected(MatchesStatusFilter.UPCOMING)
            }
            StatusChip(Res.string.matches_status_finished, state.matchesStatus == MatchesStatusFilter.FINISHED) {
                viewModel.onMatchesStatusSelected(MatchesStatusFilter.FINISHED)
            }
            StatusChip(Res.string.matches_status_all, state.matchesStatus == MatchesStatusFilter.ALL) {
                viewModel.onMatchesStatusSelected(MatchesStatusFilter.ALL)
            }
        }

        if (groups.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.msg_no_matches_for_filters),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DeepCarbon.copy(alpha = 0.55f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groups.forEachIndexed { index, group ->
                    item(key = "h-$index") {
                        Text(
                            stringResource(group.label).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = DeepCarbon.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                        )
                    }
                    items(group.matches, key = { it.id }) { match ->
                        MatchRow(
                            match = match,
                            teamName = teamsById[match.teamId]?.name ?: "—",
                            opponentName = opponentsById[match.opponentId]?.let { it.shortName ?: it.name } ?: "—"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: StringResource, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) DeepCarbon else CarbonGray.copy(alpha = 0.08f),
        border = if (selected) null else BorderStroke(1.dp, CarbonGray.copy(alpha = 0.2f))
    ) {
        Text(
            stringResource(label),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (selected) Color.White else DeepCarbon
        )
    }
}

@Composable
private fun MatchRow(match: Match, teamName: String, opponentName: String) {
    val live = match.status == MatchStatus.LIVE || match.status == MatchStatus.HALF_TIME
    val finished = match.status == MatchStatus.FINISHED
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = Color.White,
        border = BorderStroke(if (live) 2.dp else 1.dp, if (live) PitchGreen else CarbonGray.copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (live) {
                LiveBlock(match)
            } else {
                DateBlock(match.dateTime, muted = finished)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    teamName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "vs $opponentName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DeepCarbon.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitleFor(match, live, finished),
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (finished) {
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = CarbonGray.copy(alpha = 0.12f)) {
                    Text(
                        stringResource(Res.string.matches_full_time),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = DeepCarbon.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

private fun subtitleFor(match: Match, live: Boolean, finished: Boolean): String {
    val loc = match.location?.takeIf { it.isNotBlank() }
    return when {
        live -> loc ?: ""
        finished -> match.dateTime.toLocalDateTime(TimeZone.currentSystemDefault()).date.let(::dayMonthLabel)
        else -> listOfNotNull(timeLabel(match.dateTime), loc).joinToString(" · ")
    }
}

@Composable
private fun LiveBlock(match: Match) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
        Surface(shape = RoundedCornerShape(4.dp), color = LiveRed) {
            Text(
                stringResource(Res.string.matches_live),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            if (match.status == MatchStatus.HALF_TIME) stringResource(Res.string.matches_half_time)
            else stringResource(Res.string.matches_minute, liveMinute(match.dateTime)),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = PitchGreen
        )
    }
}

@Composable
private fun DateBlock(instant: Instant, muted: Boolean) {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val alpha = if (muted) 0.5f else 1f
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
        Text(
            dt.dayOfWeek.name.take(3),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = PitchGreen.copy(alpha = alpha)
        )
        Text(
            dt.day.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = DeepCarbon.copy(alpha = alpha)
        )
    }
}

// ---- Grouping ----

private class MatchGroup(val label: StringResource, val matches: List<Match>)

private fun buildGroups(state: ClubDetailsUiState.Idle, teamsById: Map<String, Team>): List<MatchGroup> {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val weekendDays = weekendAhead(today)

    val filtered = state.matches.filter { m ->
        if (m.status == MatchStatus.CANCELLED) return@filter false
        val team = teamsById[m.teamId] ?: return@filter false
        val bySeason = state.selectedMatchesSeasonId == null || team.seasonId == state.selectedMatchesSeasonId
        val byAge = state.selectedMatchesAgeGroupId == null || team.ageGroupId == state.selectedMatchesAgeGroupId
        val byTeam = state.selectedMatchesTeamId == null || m.teamId == state.selectedMatchesTeamId
        val byStatus = when (state.matchesStatus) {
            MatchesStatusFilter.UPCOMING -> m.status != MatchStatus.FINISHED
            MatchesStatusFilter.FINISHED -> m.status == MatchStatus.FINISHED
            MatchesStatusFilter.ALL -> true
        }
        bySeason && byAge && byTeam && byStatus
    }

    val upcoming = filtered.filter { it.status != MatchStatus.FINISHED }.sortedBy { it.dateTime }
    val finished = filtered.filter { it.status == MatchStatus.FINISHED }.sortedByDescending { it.dateTime }

    fun bucket(m: Match): StringResource {
        if (m.status == MatchStatus.LIVE || m.status == MatchStatus.HALF_TIME) return Res.string.matches_group_today
        val d = m.dateTime.toLocalDateTime(tz).date
        return when {
            d < today -> Res.string.matches_group_earlier
            d == today -> Res.string.matches_group_later_today
            d in weekendDays -> Res.string.matches_group_this_weekend
            else -> Res.string.matches_group_later
        }
    }

    val order = listOf(
        Res.string.matches_group_earlier,
        Res.string.matches_group_today,
        Res.string.matches_group_later_today,
        Res.string.matches_group_this_weekend,
        Res.string.matches_group_later
    )
    val upcomingGroups = upcoming.groupBy(::bucket)
        .toList()
        .sortedBy { order.indexOf(it.first) }
        .map { MatchGroup(it.first, it.second) }

    return when (state.matchesStatus) {
        MatchesStatusFilter.UPCOMING -> upcomingGroups
        MatchesStatusFilter.FINISHED ->
            if (finished.isEmpty()) emptyList() else listOf(MatchGroup(Res.string.matches_group_results, finished))
        MatchesStatusFilter.ALL ->
            upcomingGroups + if (finished.isEmpty()) emptyList()
            else listOf(MatchGroup(Res.string.matches_group_results, finished))
    }
}

/** The dates of the coming Saturday and Sunday (today counts if today is Sat/Sun). */
private fun weekendAhead(today: LocalDate): Set<LocalDate> {
    val toSat = (DayOfWeek.SATURDAY.isoDayNumber - today.dayOfWeek.isoDayNumber + 7) % 7
    val sat = today.plus(toSat, DateTimeUnit.DAY)
    return setOf(sat, sat.plus(1, DateTimeUnit.DAY))
}

private fun liveMinute(kickoff: Instant): Int {
    val mins = (Clock.System.now() - kickoff).inWholeMinutes
    return mins.coerceIn(1, 130).toInt()
}

private fun timeLabel(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
}

private val MONTH_ABBR = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private fun dayMonthLabel(date: LocalDate): String = "${date.day} ${MONTH_ABBR[date.month.ordinal]}"
