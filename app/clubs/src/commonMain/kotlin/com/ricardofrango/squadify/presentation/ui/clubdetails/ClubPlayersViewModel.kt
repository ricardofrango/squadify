package com.ricardofrango.squadify.presentation.ui.clubdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.ClubDetails
import com.ricardofrango.squadify.domain.usecase.DisablePlayerUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubPlayersUseCase
import com.ricardofrango.squadify.domain.usecase.ReactivatePlayerUseCase
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Paging
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.presentation.errorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_disable_player_failed
import squadify.app.shared.generated.resources.msg_failed_to_load_club_details
import squadify.app.shared.generated.resources.msg_reactivate_player_failed

/** The club's player list, opened from the More panel as its own screen (no bottom bar). */
sealed class ClubPlayersUiState {
    data object Loading : ClubPlayersUiState()
    data class Error(val message: String) : ClubPlayersUiState()

    data class Idle(
        val details: ClubDetails,
        // Defaults to the most recent season (by end date), as the old Players tab did.
        val seasonId: String? = null,
        val ageGroupId: String? = null,
        val query: String = "",
        // The pages loaded so far for these filters, which run on the server: players in the season's
        // or the age group's pools (a season also lists players in no pool yet), whose name matches the
        // search, active ones first, then by name.
        val players: List<Player> = emptyList(),
        // False until the first page for the current filters has arrived.
        val loaded: Boolean = false,
        val hasMore: Boolean = false,
        val loadingMore: Boolean = false,
        // Whether the club has any player the viewer may see, whatever the filters: which empty state.
        val anyPlayers: Boolean = false
    ) : ClubPlayersUiState() {
        /** The age-group filter's options: those of the selected season (all, with no season). */
        val availableAgeGroups: List<AgeGroup>
            get() = details.ageGroups.filter { seasonId == null || it.seasonId == seasonId }
    }
}

/** [players] with [playerId] marked active or disabled, in place: still in their age groups' pools. */
internal fun List<Player>.withActive(playerId: String, active: Boolean): List<Player> =
    map { if (it.id == playerId) it.copy(active = active) else it }

class ClubPlayersViewModel(
    private val getClubDetailsUseCase: GetClubDetailsUseCase,
    private val getClubPlayersUseCase: GetClubPlayersUseCase,
    private val disablePlayerUseCase: DisablePlayerUseCase,
    private val reactivatePlayerUseCase: ReactivatePlayerUseCase
) : ViewModel() {

    // One-off results of disabling a player, for the snackbar: the name, or the error (Failure).
    private val _disabled = MutableSharedFlow<Result<String>>()
    val disabled: SharedFlow<Result<String>> = _disabled.asSharedFlow()

    // Likewise for reactivating one.
    private val _reactivated = MutableSharedFlow<Result<String>>()
    val reactivated: SharedFlow<Result<String>> = _reactivated.asSharedFlow()

    private val _state = MutableStateFlow<ClubPlayersUiState>(ClubPlayersUiState.Loading)
    val state: StateFlow<ClubPlayersUiState> = _state.asStateFlow()

    private var loadedClubId: String? = null

    // The page load under way, cancelled when a filter changes.
    private var playersJob: Job? = null

    /** Loads once per club; [force] reloads (e.g. back from adding players), keeping the filters. */
    fun load(clubId: String, force: Boolean = false) {
        if (!force && loadedClubId == clubId) return
        loadedClubId = clubId
        val previous = _state.value as? ClubPlayersUiState.Idle
        viewModelScope.launch {
            if (previous == null) _state.value = ClubPlayersUiState.Loading
            try {
                val details = getClubDetailsUseCase(clubId)
                _state.value = previous?.copy(details = details)
                    ?: ClubPlayersUiState.Idle(details, seasonId = details.seasons.maxByOrNull { it.endDate }?.id)
                // As many rows as were on screen come back, so the list doesn't shrink under the user.
                reloadPlayers(atLeast = maxOf(Paging.PAGE_SIZE, previous?.players?.size ?: 0))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ClubPlayersUiState.Error(errorMessage(e, Res.string.msg_failed_to_load_club_details))
            }
        }
    }

    /**
     * Loads the first pages for the current filters, enough for [atLeast] rows, replacing the list.
     * [debounce] waits for the typing to pause first, so a search isn't sent on every key.
     */
    private fun reloadPlayers(atLeast: Int = Paging.PAGE_SIZE, debounce: Boolean = false) {
        playersJob?.cancel()
        playersJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            val filters = _state.value as? ClubPlayersUiState.Idle ?: return@launch
            val players = mutableListOf<Player>()
            var anyPlayers = false
            var hasMore: Boolean
            try {
                do {
                    val page = getClubPlayersUseCase(filters.details.club.id, filters.seasonId, filters.ageGroupId, filters.query, players.size)
                    players += page.players
                    hasMore = page.hasMore
                    anyPlayers = anyPlayers || page.anyPlayers
                } while (hasMore && players.size < atLeast)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ClubPlayersUiState.Error(errorMessage(e, Res.string.msg_failed_to_load_club_details))
                return@launch
            }
            update { it.copy(players = players, loaded = true, hasMore = hasMore, loadingMore = false, anyPlayers = anyPlayers) }
        }
    }

    /** The next page, as the list nears its end. */
    fun loadMore() {
        val current = _state.value as? ClubPlayersUiState.Idle ?: return
        if (!current.hasMore || playersJob?.isActive == true) return
        playersJob = viewModelScope.launch {
            update { it.copy(loadingMore = true) }
            try {
                val page = getClubPlayersUseCase(current.details.club.id, current.seasonId, current.ageGroupId, current.query, current.players.size)
                update { it.copy(players = (it.players + page.players).distinctBy { p -> p.id }, hasMore = page.hasMore, loadingMore = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Scrolling back to the end tries again.
                update { it.copy(loadingMore = false) }
            }
        }
    }

    /** Disables [playerId] (MANAGE_PLAYER_STATUS); the row stays, shown as disabled. */
    fun disable(playerId: String) {
        val player = (_state.value as? ClubPlayersUiState.Idle)?.players?.firstOrNull { it.id == playerId } ?: return
        viewModelScope.launch {
            try {
                disablePlayerUseCase(playerId)
                update { it.copy(players = it.players.withActive(playerId, active = false)) }
                _disabled.emit(Result.success("${player.firstName} ${player.lastName}"))
            } catch (e: Exception) {
                _disabled.emit(Result.failure(Exception(errorMessage(e, Res.string.msg_disable_player_failed))))
            }
        }
    }

    /** Reactivates a disabled [playerId] (MANAGE_PLAYER_STATUS); they can be called up again. */
    fun reactivate(playerId: String) {
        val player = (_state.value as? ClubPlayersUiState.Idle)?.players?.firstOrNull { it.id == playerId } ?: return
        viewModelScope.launch {
            try {
                reactivatePlayerUseCase(playerId)
                update { it.copy(players = it.players.withActive(playerId, active = true)) }
                _reactivated.emit(Result.success("${player.firstName} ${player.lastName}"))
            } catch (e: Exception) {
                _reactivated.emit(Result.failure(Exception(errorMessage(e, Res.string.msg_reactivate_player_failed))))
            }
        }
    }

    // Each filter runs on the server: a change loads the first page again (the spinner meanwhile).
    fun onSeasonSelected(seasonId: String?) = onFiltered { it.copy(seasonId = seasonId, ageGroupId = null) }
    fun onAgeGroupSelected(ageGroupId: String?) = onFiltered { it.copy(ageGroupId = ageGroupId) }

    // The list on screen stays while typing, until the search's own results arrive.
    fun onSearchChanged(query: String) {
        update { it.copy(query = query) }
        reloadPlayers(debounce = true)
    }

    private fun onFiltered(block: (ClubPlayersUiState.Idle) -> ClubPlayersUiState.Idle) {
        update { block(it).copy(players = emptyList(), loaded = false, hasMore = false) }
        reloadPlayers()
    }

    private fun update(block: (ClubPlayersUiState.Idle) -> ClubPlayersUiState.Idle) {
        _state.update { (it as? ClubPlayersUiState.Idle)?.let(block) ?: it }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
