package com.ricardofrango.squadify.presentation.ui.clubdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetClubDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubHomeUseCase
import com.ricardofrango.squadify.domain.usecase.GetMatchScoreUseCase
import com.ricardofrango.squadify.domain.usecase.GetMatchesUseCase
import com.ricardofrango.squadify.domain.usecase.GetOpponentsUseCase
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Team
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_failed_to_load_club_details
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class ClubDetailsViewModel(
    private val getClubDetailsUseCase: GetClubDetailsUseCase,
    private val getClubHomeUseCase: GetClubHomeUseCase,
    private val getMatchesUseCase: GetMatchesUseCase,
    private val getMatchScoreUseCase: GetMatchScoreUseCase,
    private val getOpponentsUseCase: GetOpponentsUseCase
) : ViewModel() {

    // Starts in Loading, not Idle(): loadClubDetails() always runs immediately on mount (from the
    // screen's LaunchedEffect), so an initial Idle() only bought a one-frame gap where none of the
    // screen's `when` branches match (error/showNoSeasonsState/details are all null/false) and nothing
    // renders -- during a nav transition that let the previous screen (e.g. Dashboard's pull-to-refresh
    // spinner) stay visible instead of this screen's own full-screen loading indicator.
    private val _state = MutableStateFlow<ClubDetailsUiState>(ClubDetailsUiState.Loading)
    val state: StateFlow<ClubDetailsUiState> = _state.asStateFlow()

    fun loadClubDetails(clubId: String, forceReload: Boolean = false) {
        val currentState = _state.value

        // If we already have details for this club, don't trigger a full reload/flicker -- unless the
        // caller knows the data is stale (e.g. just returned from creating a season) and explicitly
        // asks to bypass this.
        if (!forceReload && currentState is ClubDetailsUiState.Idle && currentState.details?.club?.id == clubId) {
            return
        }

        // Capture current tab to preserve it across loading states
        val currentTab = (currentState as? ClubDetailsUiState.Idle)?.selectedTab ?: CLUB_TAB_HOME

        viewModelScope.launch {
            _state.update { ClubDetailsUiState.Loading }
            try {
                val details = getClubDetailsUseCase(clubId)
                // Best-effort: the Home tab is nice-to-have, so a failure here shouldn't block the
                // rest of the club screen -- the Home tab renders its own light empty state.
                val home = try {
                    getClubHomeUseCase(clubId)
                } catch (e: Exception) {
                    null
                }
                // Same best-effort treatment for the Matches tab's data.
                val matches = try {
                    getMatchesUseCase(clubId)
                } catch (e: Exception) {
                    emptyList()
                }
                // Matches don't carry a score on Match itself (it's derived from events) -- fetch it
                // per match, for anything with a score worth showing (finished results, and live/
                // half-time matches so the Matches tab shows the current score, not just the minute).
                // Best-effort and per-match so one bad fetch doesn't blank every result.
                val matchScores = try {
                    coroutineScope {
                        matches.filter {
                            it.status == MatchStatus.FINISHED || it.status == MatchStatus.LIVE || it.status == MatchStatus.HALF_TIME
                        }
                            .map { match -> async { match.id to runCatching { getMatchScoreUseCase(match.id) }.getOrNull() } }
                            .awaitAll()
                            .mapNotNull { (id, score) -> score?.let { id to it } }
                            .toMap()
                    }
                } catch (e: Exception) {
                    emptyMap()
                }
                val opponents = try {
                    getOpponentsUseCase(clubId)
                } catch (e: Exception) {
                    emptyList()
                }
                // Teams and players are only meaningful within a season, so both tabs default to the
                // most recent one (by end date) rather than "All seasons".
                val mostRecentSeasonId = details.seasons.maxByOrNull { it.endDate }?.id
                _state.update {
                    applyFilters(
                        ClubDetailsUiState.Idle(
                            details = details,
                            home = home,
                            matches = matches,
                            matchScores = matchScores,
                            opponents = opponents,
                            selectedTab = currentTab, // Restore the captured tab
                            selectedSeasonId = mostRecentSeasonId,
                            selectedPlayerSeasonId = mostRecentSeasonId,
                            selectedMatchesSeasonId = mostRecentSeasonId,
                            showNoSeasonsState = details.seasons.isEmpty(),
                            canEditClub = details.canEditClub,
                            // Only meaningful once there's at least one season to attach age groups to --
                            // an empty club (no seasons either) is already covered by showNoSeasonsState.
                            showNoAgeGroupsState = details.seasons.isNotEmpty() && details.ageGroups.isEmpty(),
                            selectedAgeGroupsSeasonId = mostRecentSeasonId,
                        )
                    )
                }
            } catch (e: Exception) {
                // Never surface e.message to the UI: it's an implementation detail (can leak server
                // internals, exception class names, etc.), not user-facing copy. See AI_GUIDELINES.md
                // "No Raw Exception Messages in UI".
                val message = getString(Res.string.msg_failed_to_load_club_details)
                _state.update { ClubDetailsUiState.Idle(error = message) }
            }
        }
    }

    fun onSeasonSelected(seasonId: String?) {
        _state.update { state ->
            if (state is ClubDetailsUiState.Idle) {
                val newState = state.copy(selectedSeasonId = seasonId)
                applyFilters(newState)
            } else state
        }
    }

    fun onAgeGroupSelected(ageGroupId: String?) {
        _state.update { state ->
            if (state is ClubDetailsUiState.Idle) {
                val newState = state.copy(selectedAgeGroupId = ageGroupId)
                applyFilters(newState)
            } else state
        }
    }

    fun onPlayerSeasonSelected(seasonId: String?) {
        _state.update { state ->
            if (state is ClubDetailsUiState.Idle) {
                val newState = state.copy(selectedPlayerSeasonId = seasonId)
                applyFilters(newState)
            } else state
        }
    }

    fun onPlayerAgeGroupSelected(ageGroupId: String?) {
        _state.update { state ->
            if (state is ClubDetailsUiState.Idle) {
                val newState = state.copy(selectedPlayerAgeGroupId = ageGroupId)
                applyFilters(newState)
            } else state
        }
    }

    fun onPlayerSearchChanged(query: String) {
        _state.update { state ->
            if (state is ClubDetailsUiState.Idle) {
                val newState = state.copy(playerSearchQuery = query)
                applyFilters(newState)
            } else state
        }
    }

    fun onAgeGroupsSeasonSelected(seasonId: String) {
        _state.update { state ->
            if (state is ClubDetailsUiState.Idle) state.copy(selectedAgeGroupsSeasonId = seasonId) else state
        }
    }

    // ---- Matches tab filters (the list itself is filtered + grouped in the screen) ----

    fun onMatchesSeasonSelected(seasonId: String?) = updateMatchesIdle {
        // A different season -> the picked age group / team may not belong to it; reset them.
        it.copy(selectedMatchesSeasonId = seasonId, selectedMatchesAgeGroupId = null, selectedMatchesTeamId = null)
    }

    fun onMatchesAgeGroupSelected(ageGroupId: String?) = updateMatchesIdle {
        it.copy(selectedMatchesAgeGroupId = ageGroupId, selectedMatchesTeamId = null)
    }

    fun onMatchesTeamSelected(teamId: String?) = updateMatchesIdle {
        it.copy(selectedMatchesTeamId = teamId)
    }

    fun onMatchesStatusSelected(status: MatchesStatusFilter) = updateMatchesIdle {
        it.copy(matchesStatus = status)
    }

    private inline fun updateMatchesIdle(block: (ClubDetailsUiState.Idle) -> ClubDetailsUiState.Idle) {
        _state.update { if (it is ClubDetailsUiState.Idle) block(it) else it }
    }

    fun onTabSelected(index: Int) {
        _state.update { state ->
            if (state is ClubDetailsUiState.Idle) {
                state.copy(selectedTab = index)
            } else state
        }
    }

    private fun applyFilters(state: ClubDetailsUiState.Idle): ClubDetailsUiState.Idle {
        val details = state.details ?: return state
        
        // Filter Teams List based on direct relations
        val filteredTeams = details.teams.filter { team ->
            (state.selectedSeasonId == null || team.seasonId == state.selectedSeasonId) &&
            (state.selectedAgeGroupId == null || team.ageGroupId == state.selectedAgeGroupId)
        }
        
        // Filter Players List by age-group membership (players belong to age-group pools, not teams).
        val filteredPlayers = details.players.filter { player ->
            val playerAgeGroupIds = details.ageGroupPlayers
                .filter { it.playerId == player.id }
                .map { it.ageGroupId }
                .toSet()

            val matchesAgeGroup = state.selectedPlayerAgeGroupId == null ||
                playerAgeGroupIds.contains(state.selectedPlayerAgeGroupId)

            val matchesSeason = when {
                state.selectedPlayerSeasonId == null -> true
                // A player who isn't in any age-group pool yet has no season context -- surface them
                // (e.g. just registered to the club) instead of hiding them behind the season filter
                // until someone assigns them, which otherwise makes registration look like it did
                // nothing. A specific age-group filter still excludes them.
                playerAgeGroupIds.isEmpty() -> true
                else -> details.ageGroups.any {
                    it.id in playerAgeGroupIds && it.seasonId == state.selectedPlayerSeasonId
                }
            }

            val query = state.playerSearchQuery.trim().lowercase()
            val matchesSearch = query.isEmpty() ||
                "${player.firstName} ${player.lastName}".lowercase().contains(query)

            matchesSeason && matchesAgeGroup && matchesSearch
        }
        
        return state.copy(
            filteredTeams = filteredTeams,
            filteredPlayers = filteredPlayers
        )
    }

    // Helper functions to get contextual options for the UI
    fun getAvailableAgeGroupsForTeams(state: ClubDetailsUiState.Idle): List<AgeGroup> {
        return state.details?.ageGroups?.filter { 
            state.selectedSeasonId == null || it.seasonId == state.selectedSeasonId 
        } ?: emptyList()
    }

    fun getAvailableTeamsForTeams(state: ClubDetailsUiState.Idle): List<Team> {
        return state.details?.teams?.filter { 
            (state.selectedSeasonId == null || it.seasonId == state.selectedSeasonId) &&
            (state.selectedAgeGroupId == null || it.ageGroupId == state.selectedAgeGroupId)
        } ?: emptyList()
    }

    fun getAvailableAgeGroupsForPlayers(state: ClubDetailsUiState.Idle): List<AgeGroup> {
        return state.details?.ageGroups?.filter { 
            state.selectedPlayerSeasonId == null || it.seasonId == state.selectedPlayerSeasonId 
        } ?: emptyList()
    }

    /** Age groups for the season selected on the Age Groups tab, youngest (highest birth year) first. */
    fun getAgeGroupsForAgeGroupsTab(state: ClubDetailsUiState.Idle): List<AgeGroup> {
        val seasonId = state.selectedAgeGroupsSeasonId ?: state.details?.seasons?.firstOrNull()?.id
        return state.details?.ageGroups
            ?.filter { it.seasonId == seasonId }
            ?.sortedByDescending { it.birthYear }
            ?: emptyList()
    }
}
