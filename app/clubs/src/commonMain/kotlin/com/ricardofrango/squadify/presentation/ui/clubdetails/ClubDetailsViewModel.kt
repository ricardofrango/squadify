package com.ricardofrango.squadify.presentation.ui.clubdetails

import com.ricardofrango.squadify.model.ClubMatchesUpdate
import com.ricardofrango.squadify.domain.service.LiveFeedEvent
import com.ricardofrango.squadify.presentation.ui.theme.ClubThemeStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.usecase.GetClubDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubsUseCase
import com.ricardofrango.squadify.domain.usecase.GetCurrentUserUseCase
import com.ricardofrango.squadify.domain.usecase.LogoutUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubHomeUseCase
import com.ricardofrango.squadify.domain.usecase.GetMatchesUseCase
import com.ricardofrango.squadify.domain.usecase.GetOpponentsUseCase
import com.ricardofrango.squadify.domain.usecase.ObserveClubMatchesUseCase
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchListStatus
import com.ricardofrango.squadify.model.MatchesPage
import com.ricardofrango.squadify.model.Paging
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.presentation.errorMessage
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_failed_to_load_club_details
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * What the club screen can ask of its ViewModel -- an interface so the screen's layout
 * ([ClubDetailsContent]) can be previewed with a stand-in that does nothing.
 */
interface ClubDetailsActions {
    fun loadClubDetails(clubId: String, forceReload: Boolean = false)
    // Pull-to-refresh on Home, and the failure message once it's been shown.
    fun refreshHome(clubId: String)
    fun onHomeRefreshFailedShown()
    fun loadMoreAccount()
    fun logout(onLoggedOut: () -> Unit)
    fun onTabSelected(index: Int)
    fun onSeasonSelected(seasonId: String?)
    fun onAgeGroupSelected(ageGroupId: String?)
    fun onAgeGroupsSeasonSelected(seasonId: String)
    fun onMatchesSeasonSelected(seasonId: String?)
    fun onMatchesAgeGroupSelected(ageGroupId: String?)
    fun onMatchesTeamSelected(teamId: String?)
    fun onMatchesStatusSelected(status: MatchesStatusFilter)
    fun connectToClubMatchesUpdates(clubId: String)
    fun disconnectFromClubMatchesUpdates()
    fun refreshMatches(clubId: String)
    fun loadMoreMatches()
}

class ClubDetailsViewModel(
    private val getClubDetailsUseCase: GetClubDetailsUseCase,
    private val getClubHomeUseCase: GetClubHomeUseCase,
    private val getMatchesUseCase: GetMatchesUseCase,
    private val getOpponentsUseCase: GetOpponentsUseCase,
    private val observeClubMatchesUseCase: ObserveClubMatchesUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getClubsUseCase: GetClubsUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val appConfig: AppConfig,
    private val clubThemeStore: ClubThemeStore
) : ViewModel(), ClubDetailsActions {

    // ---- The "More" panel (design 37): who's signed in, how many clubs they're in, the app version ----

    private val _moreAccount = MutableStateFlow<MoreAccount?>(null)
    val moreAccount: StateFlow<MoreAccount?> = _moreAccount.asStateFlow()

    /**
     * Loads the panel's account header and club count each time it opens (the photo or name may have
     * changed in Account); the last one stays on screen meanwhile.
     */
    override fun loadMoreAccount() {
        viewModelScope.launch {
            try {
                val user = getCurrentUserUseCase()
                val clubs = try { getClubsUseCase().size } catch (e: Exception) { 1 }
                _moreAccount.value = MoreAccount("${user.firstName} ${user.lastName}".trim(), user.email, clubs, appConfig.appVersion, user.avatarUrl)
            } catch (e: Exception) {
                // Best-effort: the panel still works without the header details.
            }
        }
    }

    /** Signs out (as Account does) and then calls [onLoggedOut] to leave for Sign in. */
    override fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            try { logoutUseCase() } catch (e: Exception) { /* the session is cleared locally regardless */ }
            onLoggedOut()
        }
    }

    // Collects the club-wide matches-list socket (see MatchesTab's DisposableEffect for the
    // connect-on-tab-enter/disconnect-on-tab-leave lifecycle this belongs to) -- an explicit Job so
    // disconnectFromClubMatchesUpdates() can cancel it independently of viewModelScope, since this
    // ViewModel outlives a single visit to the Matches tab.
    private var clubMatchesJob: Job? = null

    // Starts in Loading, not Idle(): loadClubDetails() always runs immediately on mount (from the
    // screen's LaunchedEffect), so an initial Idle() only bought a one-frame gap where none of the
    // screen's `when` branches match (error/showNoSeasonsState/details are all null/false) and nothing
    // renders -- during a nav transition that let the previous screen (e.g. Dashboard's pull-to-refresh
    // spinner) stay visible instead of this screen's own full-screen loading indicator.
    private val _state = MutableStateFlow<ClubDetailsUiState>(ClubDetailsUiState.Loading)
    val state: StateFlow<ClubDetailsUiState> = _state.asStateFlow()

    override fun loadClubDetails(clubId: String, forceReload: Boolean) {
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
                clubThemeStore.remember(details.club)
                // Best-effort: the Home tab is nice-to-have, so a failure here shouldn't block the
                // rest of the club screen -- the Home tab renders its own light empty state.
                val home = try {
                    getClubHomeUseCase(clubId)
                } catch (e: Exception) {
                    null
                }
                // The Matches tab's own pages load when it opens (see refreshMatches); its opponent
                // names get the same best-effort treatment as Home.
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
                            opponents = opponents,
                            selectedTab = currentTab, // Restore the captured tab
                            selectedSeasonId = mostRecentSeasonId,
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
                val message = errorMessage(e, Res.string.msg_failed_to_load_club_details)
                _state.update { ClubDetailsUiState.Idle(error = message) }
            }
        }
    }

    /**
     * Pull-to-refresh on Home: its figures, Next up and Needs you again, with the tab left as it is
     * (no full-screen spinner). A failure keeps what's shown and says so once.
     */
    override fun refreshHome(clubId: String) {
        val current = _state.value as? ClubDetailsUiState.Idle ?: return
        if (current.isRefreshingHome) return
        viewModelScope.launch {
            _state.update { (it as? ClubDetailsUiState.Idle)?.copy(isRefreshingHome = true, homeRefreshFailed = false) ?: it }
            try {
                val home = getClubHomeUseCase(clubId)
                _state.update { (it as? ClubDetailsUiState.Idle)?.copy(home = home, isRefreshingHome = false) ?: it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { (it as? ClubDetailsUiState.Idle)?.copy(isRefreshingHome = false, homeRefreshFailed = true) ?: it }
            }
        }
    }

    override fun onHomeRefreshFailedShown() =
        _state.update { (it as? ClubDetailsUiState.Idle)?.copy(homeRefreshFailed = false) ?: it }

    // Where the Matches tab is in its lists for the current filters, and the page load under way.
    private var matchesCursor: MatchesCursor? = null
    private var matchesJob: Job? = null

    /**
     * Loads the Matches tab's first pages for its filters, enough for [atLeast] rows, replacing what was
     * there. Each page carries the scores of its started matches, so no request per match.
     */
    private fun reloadMatches(atLeast: Int = Paging.PAGE_SIZE) {
        val start = _state.value as? ClubDetailsUiState.Idle ?: return
        val clubId = start.details?.club?.id ?: return
        matchesJob?.cancel()
        matchesJob = viewModelScope.launch {
            val cursor = MatchesCursor(start.matchesStatus)
            val matches = mutableListOf<Match>()
            val scores = mutableMapOf<String, MatchScore>()
            var anyMatches = false
            val totals = mutableMapOf<MatchListStatus, Int>()
            try {
                do {
                    val list = cursor.list
                    val page = nextMatchesPage(clubId, start, cursor)
                    matches += page.matches
                    scores += page.scores.associateBy { it.matchId }
                    anyMatches = anyMatches || page.anyMatches
                    totals[list] = page.total
                } while (cursor.hasMore && matches.size < atLeast)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Best-effort, like before paging: keep what's on screen and stop asking for more.
                matchesCursor = null
                updateMatchesIdle { it.copy(matchesLoaded = true, matchesHasMore = false, matchesLoadingMore = false) }
                return@launch
            }
            matchesCursor = cursor
            updateMatchesIdle {
                it.copy(
                    matches = matches.distinctBy { m -> m.id },
                    matchScores = scores,
                    matchesLoaded = true,
                    matchesHasMore = cursor.hasMore,
                    matchesLoadingMore = false,
                    anyMatches = anyMatches,
                    notPlayedTotal = totals[MatchListStatus.NOT_PLAYED],
                    upcomingTotal = totals[MatchListStatus.UPCOMING],
                    finishedTotal = totals[MatchListStatus.FINISHED]
                )
            }
        }
    }

    /** The Matches tab's next page, as its list nears the end. */
    override fun loadMoreMatches() {
        val start = _state.value as? ClubDetailsUiState.Idle ?: return
        val clubId = start.details?.club?.id ?: return
        val cursor = matchesCursor ?: return
        if (!cursor.hasMore || matchesJob?.isActive == true) return
        matchesJob = viewModelScope.launch {
            updateMatchesIdle { it.copy(matchesLoadingMore = true) }
            try {
                val list = cursor.list
                val page = nextMatchesPage(clubId, start, cursor)
                updateMatchesIdle {
                    it.copy(
                        // A match pushed over the socket may already be in the list.
                        matches = (it.matches + page.matches).distinctBy { m -> m.id },
                        matchScores = it.matchScores + page.scores.associateBy { score -> score.matchId },
                        matchesHasMore = cursor.hasMore,
                        matchesLoadingMore = false,
                        anyMatches = it.anyMatches || page.anyMatches,
                        notPlayedTotal = if (list == MatchListStatus.NOT_PLAYED) page.total else it.notPlayedTotal,
                        upcomingTotal = if (list == MatchListStatus.UPCOMING) page.total else it.upcomingTotal,
                        finishedTotal = if (list == MatchListStatus.FINISHED) page.total else it.finishedTotal
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Scrolling back to the end tries again.
                updateMatchesIdle { it.copy(matchesLoadingMore = false) }
            }
        }
    }

    private suspend fun nextMatchesPage(clubId: String, filters: ClubDetailsUiState.Idle, cursor: MatchesCursor): MatchesPage {
        val page = getMatchesUseCase(
            clubId, cursor.list,
            filters.selectedMatchesSeasonId, filters.selectedMatchesAgeGroupId, filters.selectedMatchesTeamId,
            cursor.offset
        )
        cursor.advance(page.matches.size, page.hasMore)
        return page
    }

    /**
     * Re-fetches the Matches tab's own data via REST (not the socket) -- used when the tab opens and
     * when returning from a match's own detail screen, where the club-matches socket was disconnected
     * while away and may have missed updates in the meantime (see MatchesTab's DisposableEffect). As
     * many rows as were loaded come back, so the list doesn't shrink under the user.
     */
    override fun refreshMatches(clubId: String) {
        val loaded = (_state.value as? ClubDetailsUiState.Idle)?.matches?.size ?: 0
        reloadMatches(atLeast = maxOf(Paging.PAGE_SIZE, loaded))
    }

    /**
     * Opens the club-wide matches-list socket for as long as the Matches tab is visible (connected in
     * MatchesTab's DisposableEffect on entry, disconnected on tab-switch-away or navigation to a
     * match). Covers both a match's own fields (status, schedule -- [ClubMatchesUpdate.match]/
     * [ClubMatchesUpdate.deletedMatchId]) and, per goal, its score badge ([ClubMatchesUpdate.score]).
     */
    override fun connectToClubMatchesUpdates(clubId: String) {
        disconnectFromClubMatchesUpdates()
        clubMatchesJob = viewModelScope.launch {
            // The socket reopens itself when it drops (e.g. the app backgrounded and the OS tore the
            // connection down); once it's back the list reloads, since what changed meanwhile was missed.
            var missedUpdates = false
            observeClubMatchesUseCase(clubId).collect { event ->
                when (event) {
                    LiveFeedEvent.Connected -> if (missedUpdates) {
                        missedUpdates = false
                        refreshMatches(clubId)
                    }
                    LiveFeedEvent.Disconnected -> missedUpdates = true
                    is LiveFeedEvent.Update -> applyClubMatchesUpdate(event.value)
                }
            }
        }
    }

    private fun applyClubMatchesUpdate(update: ClubMatchesUpdate) {
        val deletedMatchId = update.deletedMatchId
        val updatedMatch = update.match
        val updatedScore = update.score
        _state.update { state ->
            if (state !is ClubDetailsUiState.Idle) return@update state
            when {
                deletedMatchId != null -> state.copy(matches = state.matches.filterNot { it.id == deletedMatchId })
                updatedMatch != null -> {
                    val updatedMatches = if (state.matches.any { it.id == updatedMatch.id }) {
                        state.matches.map { if (it.id == updatedMatch.id) updatedMatch else it }
                    } else {
                        state.matches + updatedMatch
                    }
                    state.copy(matches = updatedMatches)
                }
                updatedScore != null -> state.copy(matchScores = state.matchScores + (updatedScore.matchId to updatedScore))
                else -> state
            }
        }
    }

    override fun disconnectFromClubMatchesUpdates() {
        clubMatchesJob?.cancel()
        clubMatchesJob = null
    }

    override fun onSeasonSelected(seasonId: String?) {
        _state.update { state ->
            if (state is ClubDetailsUiState.Idle) {
                val newState = state.copy(selectedSeasonId = seasonId)
                applyFilters(newState)
            } else state
        }
    }

    override fun onAgeGroupSelected(ageGroupId: String?) {
        _state.update { state ->
            if (state is ClubDetailsUiState.Idle) {
                val newState = state.copy(selectedAgeGroupId = ageGroupId)
                applyFilters(newState)
            } else state
        }
    }

    override fun onAgeGroupsSeasonSelected(seasonId: String) {
        _state.update { state ->
            if (state is ClubDetailsUiState.Idle) state.copy(selectedAgeGroupsSeasonId = seasonId) else state
        }
    }

    // ---- Matches tab filters (the list itself is filtered + grouped in the screen) ----

    // Each filter runs on the server, so a change loads the tab's first page again.

    override fun onMatchesSeasonSelected(seasonId: String?) = onMatchesFiltered {
        // A different season -> the picked age group / team may not belong to it; reset them.
        it.copy(selectedMatchesSeasonId = seasonId, selectedMatchesAgeGroupId = null, selectedMatchesTeamId = null)
    }

    override fun onMatchesAgeGroupSelected(ageGroupId: String?) = onMatchesFiltered {
        it.copy(selectedMatchesAgeGroupId = ageGroupId, selectedMatchesTeamId = null)
    }

    override fun onMatchesTeamSelected(teamId: String?) = onMatchesFiltered {
        it.copy(selectedMatchesTeamId = teamId)
    }

    override fun onMatchesStatusSelected(status: MatchesStatusFilter) = onMatchesFiltered {
        it.copy(matchesStatus = status)
    }

    // The new filters' list replaces the old one; until it arrives the tab shows its spinner.
    private inline fun onMatchesFiltered(block: (ClubDetailsUiState.Idle) -> ClubDetailsUiState.Idle) {
        updateMatchesIdle {
            block(it).copy(matches = emptyList(), matchScores = emptyMap(), matchesLoaded = false, matchesHasMore = false, notPlayedTotal = null, upcomingTotal = null, finishedTotal = null)
        }
        reloadMatches()
    }

    private inline fun updateMatchesIdle(block: (ClubDetailsUiState.Idle) -> ClubDetailsUiState.Idle) {
        _state.update { if (it is ClubDetailsUiState.Idle) block(it) else it }
    }

    override fun onTabSelected(index: Int) {
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
        
        return state.copy(filteredTeams = filteredTeams)
    }

    // Helper functions to get contextual options for the UI
    fun getAvailableAgeGroupsForTeams(state: ClubDetailsUiState.Idle): List<AgeGroup> = state.availableAgeGroupsForTeams()

    fun getAvailableTeamsForTeams(state: ClubDetailsUiState.Idle): List<Team> {
        return state.details?.teams?.filter { 
            (state.selectedSeasonId == null || it.seasonId == state.selectedSeasonId) &&
            (state.selectedAgeGroupId == null || it.ageGroupId == state.selectedAgeGroupId)
        } ?: emptyList()
    }

    /** Age groups for the season selected on the Age Groups tab, youngest (highest birth year) first. */
    fun getAgeGroupsForAgeGroupsTab(state: ClubDetailsUiState.Idle): List<AgeGroup> = state.ageGroupsForAgeGroupsTab()
}
