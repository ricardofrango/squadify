package com.ricardofrango.squadify.presentation.ui.addplayerstoagegroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.AddPlayersToAgeGroupUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupsUseCase
import com.ricardofrango.squadify.domain.usecase.GetEligiblePlayersForAgeGroupUseCase
import com.ricardofrango.squadify.domain.usecase.GetSeasonsUseCase
import com.ricardofrango.squadify.model.Paging
import com.ricardofrango.squadify.presentation.errorMessage
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_add_players_failed
import com.ricardofrango.squadify.model.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class AddPlayersToAgeGroupViewModel(
    private val getAgeGroupsUseCase: GetAgeGroupsUseCase,
    private val getSeasonsUseCase: GetSeasonsUseCase,
    private val getEligiblePlayersForAgeGroupUseCase: GetEligiblePlayersForAgeGroupUseCase,
    private val addPlayersToAgeGroupUseCase: AddPlayersToAgeGroupUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<AddPlayersToAgeGroupUiState>(AddPlayersToAgeGroupUiState.Loading)
    val state: StateFlow<AddPlayersToAgeGroupUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<AddPlayersToAgeGroupNavigationEvent>()
    val navigationEvent: SharedFlow<AddPlayersToAgeGroupNavigationEvent> = _navigationEvent.asSharedFlow()

    // The page load under way, cancelled when the age group or the search changes.
    private var eligibleJob: Job? = null

    fun load(clubId: String, initialAgeGroupId: String? = null) {
        if (_state.value is AddPlayersToAgeGroupUiState.Idle) return

        viewModelScope.launch {
            val seasons = try {
                getSeasonsUseCase(clubId)
            } catch (e: Exception) {
                emptyList()
            }
            val ageGroups = try {
                getAgeGroupsUseCase(clubId)
            } catch (e: Exception) {
                emptyList()
            }
            // Honour a caller-supplied pre-selection; otherwise default to the age group in the most
            // recently ending season (tiebreak oldest players).
            val selectedAgeGroupId = initialAgeGroupId?.takeIf { id -> ageGroups.any { it.id == id } }
                ?: ageGroups
                    .sortedWith(
                        compareByDescending<com.ricardofrango.squadify.model.AgeGroup> {
                            seasons.find { s -> s.id == it.seasonId }?.endDate
                        }.thenBy { it.endYear }
                    )
                    .firstOrNull()
                    ?.id

            _state.update {
                AddPlayersToAgeGroupUiState.Idle(
                    ageGroups = ageGroups,
                    seasons = seasons,
                    selectedAgeGroupId = selectedAgeGroupId
                )
            }
            selectedAgeGroupId?.let { loadEligible(it) }
        }
    }

    fun onAgeGroupSelected(ageGroupId: String) {
        val current = _state.value as? AddPlayersToAgeGroupUiState.Idle ?: return
        if (ageGroupId == current.selectedAgeGroupId) return
        _state.update {
            current.copy(
                selectedAgeGroupId = ageGroupId,
                selectedPlayerIds = emptySet(),
                eligiblePlayers = emptyList(),
                loadingEligible = true,
                query = "",
                hasMore = false,
                error = null
            )
        }
        loadEligible(ageGroupId)
    }

    // The list on screen stays while typing, until the search's own results arrive.
    fun onSearchChanged(query: String) {
        val current = _state.value as? AddPlayersToAgeGroupUiState.Idle ?: return
        _state.update { current.copy(query = query) }
        current.selectedAgeGroupId?.let { loadEligible(it, debounce = true) }
    }

    /**
     * Loads the first pages for the current search, enough for [atLeast] rows, replacing the list.
     * [debounce] waits for the typing to pause first, so a search isn't sent on every key.
     */
    private fun loadEligible(ageGroupId: String, atLeast: Int = Paging.PAGE_SIZE, debounce: Boolean = false) {
        eligibleJob?.cancel()
        eligibleJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            val query = (_state.value as? AddPlayersToAgeGroupUiState.Idle)?.query.orEmpty()
            val players = mutableListOf<Player>()
            var hasMore = false
            var anyEligible = false
            try {
                do {
                    val page = getEligiblePlayersForAgeGroupUseCase(ageGroupId, query, players.size)
                    players += page.players
                    hasMore = page.hasMore
                    anyEligible = anyEligible || page.anyPlayers
                } while (hasMore && players.size < atLeast)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Shown as no eligible players, as before; coming back from New player tries again.
                players.clear()
                hasMore = false
            }
            update(ageGroupId) {
                it.copy(eligiblePlayers = players, loadingEligible = false, hasMore = hasMore, loadingMore = false, anyEligible = anyEligible)
            }
        }
    }

    /** The next page, as the list nears its end. */
    fun loadMore() {
        val current = _state.value as? AddPlayersToAgeGroupUiState.Idle ?: return
        val ageGroupId = current.selectedAgeGroupId ?: return
        if (!current.hasMore || eligibleJob?.isActive == true) return
        eligibleJob = viewModelScope.launch {
            update(ageGroupId) { it.copy(loadingMore = true) }
            try {
                val page = getEligiblePlayersForAgeGroupUseCase(ageGroupId, current.query, current.eligiblePlayers.size)
                update(ageGroupId) {
                    it.copy(eligiblePlayers = (it.eligiblePlayers + page.players).distinctBy { p -> p.id }, hasMore = page.hasMore, loadingMore = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Scrolling back to the end tries again.
                update(ageGroupId) { it.copy(loadingMore = false) }
            }
        }
    }

    // Only while [ageGroupId] is still the one picked: a late answer for another is dropped.
    private fun update(ageGroupId: String, block: (AddPlayersToAgeGroupUiState.Idle) -> AddPlayersToAgeGroupUiState.Idle) {
        _state.update { state ->
            if (state is AddPlayersToAgeGroupUiState.Idle && state.selectedAgeGroupId == ageGroupId) block(state) else state
        }
    }

    /**
     * Re-fetch the eligible list for the currently selected age group, keeping the current
     * selection -- called when the user comes back from registering new club players via the
     * "New player" shortcut, so those players appear without leaving the screen.
     */
    fun refreshEligiblePlayers() {
        val current = _state.value as? AddPlayersToAgeGroupUiState.Idle ?: return
        val ageGroupId = current.selectedAgeGroupId ?: return
        _state.update { current.copy(error = null) }
        // As many rows as were on screen come back, so the list doesn't shrink under the user.
        loadEligible(ageGroupId, atLeast = maxOf(Paging.PAGE_SIZE, current.eligiblePlayers.size))
    }

    fun onPlayerToggled(playerId: String) {
        _state.update { state ->
            if (state is AddPlayersToAgeGroupUiState.Idle) {
                val next = if (playerId in state.selectedPlayerIds) {
                    state.selectedPlayerIds - playerId
                } else {
                    state.selectedPlayerIds + playerId
                }
                state.copy(selectedPlayerIds = next, error = null)
            } else {
                state
            }
        }
    }

    fun clearError() {
        _state.update { state ->
            if (state is AddPlayersToAgeGroupUiState.Idle) state.copy(error = null) else state
        }
    }

    fun onAddClicked() {
        val current = _state.value as? AddPlayersToAgeGroupUiState.Idle ?: return
        val ageGroupId = current.selectedAgeGroupId ?: return
        if (current.selectedPlayerIds.isEmpty()) return

        viewModelScope.launch {
            _state.update { current.copy(isSubmitting = true, error = null) }
            try {
                addPlayersToAgeGroupUseCase(ageGroupId, current.selectedPlayerIds.toList())
                _navigationEvent.emit(AddPlayersToAgeGroupNavigationEvent.NavigateBackWithPlayersAdded)
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_add_players_failed)
                _state.update { current.copy(isSubmitting = false, error = message) }
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
