package com.ricardofrango.squadify.presentation.ui.clubdetails

import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.components.FilterChipRow
import com.ricardofrango.squadify.presentation.ui.theme.IsClubThemed
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.presentation.ui.components.MatchRow
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.FilterRowPadding
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import squadify.app.shared.generated.resources.*
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

/**
 * The club Matches tab (design 14A / 14B): the club-wide fixture list, so each row names its team.
 * Season / age-group / team filter pills + an Upcoming / Finished / All status control, then the
 * matches grouped into date-relative buckets. A match in progress renders as a green-bordered card
 * with a LIVE badge and running minute in place of the date.
 */
@Composable
fun MatchesTab(
    state: ClubDetailsUiState.Idle,
    actions: ClubDetailsActions,
    onGoToAgeGroups: () -> Unit,
    onNavigateToMatch: (matchId: String) -> Unit
) {
    val details = state.details ?: return

    // Live-updates the fixture list for as long as this tab is on screen: connects when this
    // composable enters composition (tab selected, or returning from a match's own detail screen,
    // which disposed this same composable while it was open) and disconnects when it leaves (a
    // different tab selected, or navigating to a match). A REST refresh runs alongside the
    // (re)connect so a stale list is corrected immediately rather than waiting for the next socket
    // event, since the socket only pushes -- it carries no backlog for whatever changed while away.
    DisposableEffect(details.club.id) {
        actions.refreshMatches(details.club.id)
        actions.connectToClubMatchesUpdates(details.club.id)
        onDispose { actions.disconnectFromClubMatchesUpdates() }
    }

    // The club has no fixtures at all -- the full empty state, no filter row.
    if (state.matches.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().background(ScreenBackground).screenContentPadding(),
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
            // Same primary CTA styling as EmptyStateCallToAction -- a bare OutlinedButton here
            // rendered in the muted default colour instead of the green it's meant to be.
            Button(
                onClick = onGoToAgeGroups,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White)
            ) {
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

    Column(modifier = Modifier.fillMaxSize().background(ScreenBackground)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            // No gap of its own under the selectors: the status chips' touch area already leaves one.
            contentPadding = PaddingValues(start = ScreenPadding.Horizontal, end = ScreenPadding.Horizontal, top = ScreenPadding.Top),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                FilterSelector(
                    selectedOption = details.seasons.find { it.id == state.selectedMatchesSeasonId }?.name
                        ?: stringResource(Res.string.label_all_seasons),
                    options = listOf(null to stringResource(Res.string.label_all_seasons)) +
                        details.seasons.map { it.id to it.name },
                    onOptionSelected = actions::onMatchesSeasonSelected
                )
            }
            item {
                FilterSelector(
                    selectedOption = ageGroupsForSeason.find { it.id == state.selectedMatchesAgeGroupId }?.name
                        ?: stringResource(Res.string.label_all_ages),
                    options = listOf(null to stringResource(Res.string.label_all_ages)) +
                        ageGroupsForSeason.map { it.id to it.name },
                    onOptionSelected = actions::onMatchesAgeGroupSelected
                )
            }
            item {
                FilterSelector(
                    selectedOption = teamsForFilter.find { it.id == state.selectedMatchesTeamId }?.name
                        ?: stringResource(Res.string.label_all_teams),
                    options = listOf(null to stringResource(Res.string.label_all_teams)) +
                        teamsForFilter.map { it.id to it.name },
                    onOptionSelected = actions::onMatchesTeamSelected
                )
            }
        }

        FilterChipRow(
            options = listOf(MatchesStatusFilter.UPCOMING, MatchesStatusFilter.FINISHED, MatchesStatusFilter.ALL),
            selected = state.matchesStatus,
            label = { stringResource(it.labelRes()) },
            onSelect = actions::onMatchesStatusSelected
        )

        if (groups.isEmpty()) {
            Box(Modifier.fillMaxWidth().screenContentPadding(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.msg_no_matches_for_filters),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DeepCarbon.copy(alpha = 0.55f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = ScreenPadding.Horizontal, end = ScreenPadding.Horizontal, top = 0.dp, bottom = ScreenPadding.Bottom),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groups.forEachIndexed { index, group ->
                    item(key = "h-$index") {
                        Text(
                            stringResource(group.label).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = DeepCarbon.copy(alpha = 0.5f),
                            // The first group's label sits just under the status chips; later ones keep a gap.
                            modifier = Modifier.padding(top = if (index == 0) 4.dp else 12.dp, bottom = 2.dp)
                        )
                    }
                    items(group.matches, key = { it.id }) { match ->
                        MatchRow(
                            match = match,
                            teamName = teamsById[match.teamId]?.name ?: "—",
                            opponentName = opponentsById[match.opponentId]?.let { it.shortName ?: it.name } ?: "—",
                            opponentLogoUrl = opponentsById[match.opponentId]?.logoUrl,
                            clubLogoUrl = details.club.logoUrl,
                            score = state.matchScores[match.id],
                            onClick = { onNavigateToMatch(match.id) }
                        )
                    }
                }
            }
        }
    }
}

private fun MatchesStatusFilter.labelRes(): StringResource = when (this) {
    MatchesStatusFilter.UPCOMING -> Res.string.matches_status_upcoming
    MatchesStatusFilter.FINISHED -> Res.string.matches_status_finished
    MatchesStatusFilter.ALL -> Res.string.matches_status_all
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
