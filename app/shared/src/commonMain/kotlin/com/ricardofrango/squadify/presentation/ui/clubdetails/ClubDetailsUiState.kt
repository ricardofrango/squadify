package com.ricardofrango.squadify.presentation.ui.clubdetails

import com.ricardofrango.squadify.domain.usecase.ClubDetails
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.ClubHome
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.Opponent

/** Which slice of the club's fixtures the Matches tab shows. */
enum class MatchesStatusFilter { UPCOMING, FINISHED, ALL }

/** Club-screen tab indices. Bottom bar shows Home (default), Age groups, Teams, Matches, then "More",
 * which opens the More panel; Players is its own screen (ClubPlayersScreen), reached from there. */
const val CLUB_TAB_HOME = 0
const val CLUB_TAB_AGE_GROUPS = 1
const val CLUB_TAB_TEAMS = 2
const val CLUB_TAB_MATCHES = 3

sealed class ClubDetailsUiState {
    data class Idle(
        val details: ClubDetails? = null,
        // The Home tab's aggregate. Loaded alongside `details`; null while loading or if it failed
        // (the Home tab then shows its own light empty/error state).
        val home: ClubHome? = null,
        val filteredTeams: List<Team> = emptyList(),
        val selectedTab: Int = CLUB_TAB_HOME,
        // Available Filter Options (Contextual)
        val availableAgeGroups: List<AgeGroup> = emptyList(),
        val availableTeams: List<Team> = emptyList(),
        // Team Filters
        val selectedSeasonId: String? = null,
        val selectedAgeGroupId: String? = null,
        // Age Groups tab filter -- season only, and unlike the other tabs there's no "All seasons":
        // an age group only means something within one season. Defaults to the most recent season
        // (by end date) when details load.
        val selectedAgeGroupsSeasonId: String? = null,
        // Matches tab -- the pages of fixtures loaded so far for its filters + the opponent list
        // (best-effort, see ClubDetailsViewModel), plus its own filter set (season defaults to the most
        // recent, status to UPCOMING). The filters run on the server; the next page loads on scrolling.
        val matches: List<Match> = emptyList(),
        // The score of each started match in `matches`, keyed by Match.id -- sent with its page, so a
        // finished fixture shows its result and a live one its current score.
        val matchScores: Map<String, MatchScore> = emptyMap(),
        // False until the first page for the current filters has arrived (the tab shows a spinner).
        val matchesLoaded: Boolean = false,
        val matchesHasMore: Boolean = false,
        val matchesLoadingMore: Boolean = false,
        // Whether the viewer can see any match of the club at all: false shows "No matches yet".
        val anyMatches: Boolean = false,
        val opponents: List<Opponent> = emptyList(),
        val selectedMatchesSeasonId: String? = null,
        val selectedMatchesAgeGroupId: String? = null,
        val selectedMatchesTeamId: String? = null,
        val matchesStatus: MatchesStatusFilter = MatchesStatusFilter.UPCOMING,
        val error: String? = null,
        // Resolved once when details load, so the screen just reads a flag instead of re-deriving it
        // from `details` on every recomposition: everything on this screen (teams, players, filters)
        // is season-scoped, so a club with no seasons has nothing meaningful to show.
        val showNoSeasonsState: Boolean = false,
        // Whether the "no seasons" state should offer a create-season action -- only a CLUB_ADMIN,
        // COORDINATOR, or platform admin can create one (see CreateSeasonUseCase / PROCESS_FLOWS.md §3.3).
        val canEditClub: Boolean = false,
        // Same idea, one level down: a club can have seasons but no age groups yet, which leaves teams
        // and players just as unorganisable. Only checked once there's at least one season -- see
        // ClubDetailsViewModel.
        val showNoAgeGroupsState: Boolean = false,
    ) : ClubDetailsUiState()
    data object Loading : ClubDetailsUiState()
}

/** The More panel's account header: who's signed in, how many clubs they belong to, the app version. */
data class MoreAccount(val name: String, val email: String, val clubCount: Int, val appVersion: String?, val avatarUrl: String? = null)

/** The Teams tab's age-group options: those of the selected season (all, with no season). */
fun ClubDetailsUiState.Idle.availableAgeGroupsForTeams(): List<AgeGroup> =
    details?.ageGroups?.filter { selectedSeasonId == null || it.seasonId == selectedSeasonId } ?: emptyList()

/** Age groups for the season selected on the Age Groups tab, youngest (highest birth year) first. */
fun ClubDetailsUiState.Idle.ageGroupsForAgeGroupsTab(): List<AgeGroup> {
    val seasonId = selectedAgeGroupsSeasonId ?: details?.seasons?.firstOrNull()?.id
    return details?.ageGroups?.filter { it.seasonId == seasonId }?.sortedByDescending { it.endYear } ?: emptyList()
}

