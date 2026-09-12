package com.ricardofrango.clubmanager.presentation.ui.clubdetails

import com.ricardofrango.clubmanager.domain.usecase.ClubDetails
import com.ricardofrango.clubmanager.model.Team
import com.ricardofrango.clubmanager.model.Player
import com.ricardofrango.clubmanager.model.AgeGroup
import com.ricardofrango.clubmanager.model.ClubHome
import com.ricardofrango.clubmanager.model.Match
import com.ricardofrango.clubmanager.model.MatchScore
import com.ricardofrango.clubmanager.model.Opponent

/** Which slice of the club's fixtures the Matches tab shows. */
enum class MatchesStatusFilter { UPCOMING, FINISHED, ALL }

/** Club-screen tab indices. Order: Age groups, Teams, Home, Players, Matches -- Home is the default. */
const val CLUB_TAB_AGE_GROUPS = 0
const val CLUB_TAB_TEAMS = 1
const val CLUB_TAB_HOME = 2
const val CLUB_TAB_PLAYERS = 3
const val CLUB_TAB_MATCHES = 4

sealed class ClubDetailsUiState {
    data class Idle(
        val details: ClubDetails? = null,
        // The Home tab's aggregate. Loaded alongside `details`; null while loading or if it failed
        // (the Home tab then shows its own light empty/error state).
        val home: ClubHome? = null,
        val filteredTeams: List<Team> = emptyList(),
        val filteredPlayers: List<Player> = emptyList(),
        val selectedTab: Int = CLUB_TAB_HOME,
        // Available Filter Options (Contextual)
        val availableAgeGroups: List<AgeGroup> = emptyList(),
        val availableTeams: List<Team> = emptyList(),
        // Team Filters
        val selectedSeasonId: String? = null,
        val selectedAgeGroupId: String? = null,
        // Player Filters
        val selectedPlayerSeasonId: String? = null,
        val selectedPlayerAgeGroupId: String? = null,
        // Age Groups tab filter -- season only, and unlike the other tabs there's no "All seasons":
        // an age group only means something within one season. Defaults to the most recent season
        // (by end date) when details load.
        val selectedAgeGroupsSeasonId: String? = null,
        // Matches tab -- the club's fixtures + opponent list (best-effort, see ClubDetailsViewModel),
        // plus its own filter set (season defaults to the most recent, status to UPCOMING).
        val matches: List<Match> = emptyList(),
        // Final scores for `matches` entries with status FINISHED, keyed by Match.id -- best-effort,
        // fetched after `matches` loads (see ClubDetailsViewModel), so a finished fixture can show its
        // result instead of just a "FULL TIME" badge with no score.
        val matchScores: Map<String, MatchScore> = emptyMap(),
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
