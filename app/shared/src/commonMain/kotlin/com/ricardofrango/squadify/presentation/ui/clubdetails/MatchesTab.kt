package com.ricardofrango.squadify.presentation.ui.clubdetails

import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.components.SegmentedFilter
import com.ricardofrango.squadify.presentation.ui.theme.IsClubThemed
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import com.ricardofrango.squadify.presentation.ui.components.LoadMoreEffect
import com.ricardofrango.squadify.presentation.ui.components.loadingMoreItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ricardofrango.squadify.presentation.ui.components.MatchListEntry
import com.ricardofrango.squadify.presentation.ui.components.MatchListSection
import com.ricardofrango.squadify.presentation.ui.components.RECENT_RESULTS_SHOWN
import com.ricardofrango.squadify.presentation.ui.components.matchListSections
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
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.FilterRowPadding
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import squadify.app.shared.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The club Matches tab (design 14A / 14B): the club-wide fixture list, so each row names its team.
 * Season / age-group / team filter pills + an All / Upcoming / Finished status control (All by
 * default, since both lists show), then the
 * same list as a team's and an age group's Games tab (matchListSections): "Upcoming · n" and
 * "Recent results · n", a page at a time. A match in progress renders as a green-bordered card with a
 * LIVE badge and running minute in place of the date.
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
    if (state.matchesLoaded && !state.anyMatches) {
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

    val (upcoming, finished) = remember(state.matches, state.selectedMatchesSeasonId, state.selectedMatchesAgeGroupId, state.selectedMatchesTeamId, teamsById) {
        splitMatches(state, teamsById)
    }
    // "See all n" under the results, as on a team's and an age group's Games tab; a new filter folds them again.
    var showAllResults by remember(state.matchesStatus, state.selectedMatchesSeasonId, state.selectedMatchesAgeGroupId, state.selectedMatchesTeamId) {
        mutableStateOf(false)
    }
    fun entries(matches: List<Match>) = matches.map { MatchListEntry(it, state.matchScores[it.id], teamsById[it.teamId]?.name) }
    val upcomingSection = if (state.matchesStatus == MatchesStatusFilter.FINISHED) null
    else MatchListSection(entries(upcoming), total = maxOf(state.upcomingTotal ?: 0, upcoming.size))
    // Under All the results come once Upcoming has loaded to its end.
    val resultsSection = when {
        state.matchesStatus == MatchesStatusFilter.UPCOMING -> null
        state.matchesStatus == MatchesStatusFilter.ALL && state.finishedTotal == null -> null
        else -> MatchListSection(entries(finished), total = maxOf(state.finishedTotal ?: 0, finished.size))
    }
    val resultsFolded = state.matchesStatus == MatchesStatusFilter.ALL && !showAllResults

    Column(modifier = Modifier.fillMaxSize().background(ScreenBackground)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            // No gap of its own under the selectors: the status chips' row leaves one.
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

        SegmentedFilter(
            options = listOf(MatchesStatusFilter.ALL, MatchesStatusFilter.UPCOMING, MatchesStatusFilter.FINISHED),
            selected = state.matchesStatus,
            label = { stringResource(it.labelRes()) },
            onSelect = actions::onMatchesStatusSelected,
            modifier = Modifier.padding(horizontal = ScreenPadding.Horizontal, vertical = 10.dp)
        )

        if (!state.matchesLoaded) {
            Box(Modifier.fillMaxWidth().screenContentPadding(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PitchGreen)
            }
        } else {
            val listState = rememberLazyListState()
            // Folded results need no more pages than the few they show.
            val wantsMore = !(resultsFolded && resultsSection != null && finished.size > RECENT_RESULTS_SHOWN)
            LoadMoreEffect(listState, enabled = state.matchesHasMore && !state.matchesLoadingMore && wantsMore, onLoadMore = actions::loadMoreMatches)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = ScreenPadding.Horizontal, end = ScreenPadding.Horizontal, top = 4.dp, bottom = ScreenPadding.Bottom),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                matchListSections(
                    upcoming = upcomingSection,
                    results = resultsSection,
                    opponentsById = opponentsById,
                    clubLogoUrl = details.club.logoUrl,
                    showAllResults = !resultsFolded,
                    onShowAllResults = { showAllResults = true },
                    onOpenMatch = onNavigateToMatch
                )
                loadingMoreItem(state.matchesLoadingMore)
            }
        }
    }
}

private fun MatchesStatusFilter.labelRes(): StringResource = when (this) {
    MatchesStatusFilter.UPCOMING -> Res.string.matches_status_upcoming
    MatchesStatusFilter.FINISHED -> Res.string.matches_status_finished
    MatchesStatusFilter.ALL -> Res.string.matches_status_all
}

// ---- Sections ----

/**
 * The loaded matches that fit the filters (a match pushed over the socket may not), as Upcoming
 * (neither finished nor cancelled, soonest first) and Finished (newest first), like the server's lists.
 */
private fun splitMatches(state: ClubDetailsUiState.Idle, teamsById: Map<String, Team>): Pair<List<Match>, List<Match>> {
    val shown = state.matches.filter { m ->
        val team = teamsById[m.teamId] ?: return@filter false
        m.status != MatchStatus.CANCELLED &&
            (state.selectedMatchesSeasonId == null || team.seasonId == state.selectedMatchesSeasonId) &&
            (state.selectedMatchesAgeGroupId == null || team.ageGroupId == state.selectedMatchesAgeGroupId) &&
            (state.selectedMatchesTeamId == null || m.teamId == state.selectedMatchesTeamId)
    }
    val (finished, upcoming) = shown.partition { it.status == MatchStatus.FINISHED }
    return upcoming.sortedBy { it.dateTime } to finished.sortedByDescending { it.dateTime }
}
