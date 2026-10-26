package com.ricardofrango.squadify.presentation.ui.opponents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetOpponentListUseCase
import com.ricardofrango.squadify.model.OpponentList
import com.ricardofrango.squadify.model.Paging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class OpponentsUiState {
    data object Loading : OpponentsUiState()
    data object Error : OpponentsUiState()

    /**
     * [list] holds the pages loaded so far, by name, for the search [searched] (what the results are
     * for, which the field may already be ahead of while typing).
     */
    data class Idle(val list: OpponentList, val searched: String = "", val loadingMore: Boolean = false) : OpponentsUiState()
}

/**
 * The Opponents screen (design 38), in either mode -- the mode only changes the screen, not the data.
 * The list comes a page at a time, searched on the server (TECHNICAL_DECISIONS §5).
 */
class OpponentsViewModel(
    private val getOpponentListUseCase: GetOpponentListUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<OpponentsUiState>(OpponentsUiState.Loading)
    val state: StateFlow<OpponentsUiState> = _state.asStateFlow()

    // Kept outside the state so it survives a reload and stays typed while loading or on an error.
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var clubId: String? = null
    private var ageGroupId: String? = null

    // The page load under way, cancelled when the search changes.
    private var job: Job? = null

    /** [force] reloads after returning from a screen that changed opponents. */
    fun load(clubId: String, ageGroupId: String?, force: Boolean = false) {
        if (!force && _state.value is OpponentsUiState.Idle) return
        this.clubId = clubId
        this.ageGroupId = ageGroupId
        if (_state.value !is OpponentsUiState.Idle) _state.update { OpponentsUiState.Loading }
        // As many rows as were on screen come back, so the list doesn't shrink under the user.
        reload(atLeast = maxOf(Paging.PAGE_SIZE, (_state.value as? OpponentsUiState.Idle)?.list?.opponents?.size ?: 0))
    }

    // The list on screen stays while typing, until the search's own results arrive.
    fun onQueryChange(query: String) {
        _query.update { query }
        if (clubId != null) reload(debounce = true)
    }

    /** The next page, as the list nears its end. */
    fun loadMore() {
        val current = _state.value as? OpponentsUiState.Idle ?: return
        val clubId = clubId ?: return
        if (!current.list.hasMore || job?.isActive == true) return
        job = viewModelScope.launch {
            _state.value = current.copy(loadingMore = true)
            try {
                val page = getOpponentListUseCase.page(clubId, ageGroupId, current.searched, current.list.opponents.size)
                val opponents = (current.list.opponents + page.opponents).distinctBy { it.id }
                _state.value = current.copy(list = page.copy(opponents = opponents), loadingMore = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Scrolling back to the end tries again.
                _state.value = current.copy(loadingMore = false)
            }
        }
    }

    /**
     * Loads the first pages for the current search, enough for [atLeast] rows, replacing the list.
     * [debounce] waits for the typing to pause first, so a search isn't sent on every key.
     */
    private fun reload(atLeast: Int = Paging.PAGE_SIZE, debounce: Boolean = false) {
        val clubId = clubId ?: return
        job?.cancel()
        job = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            val query = _query.value.trim()
            try {
                var list = getOpponentListUseCase.page(clubId, ageGroupId, query, 0)
                while (list.hasMore && list.opponents.size < atLeast) {
                    val page = getOpponentListUseCase.page(clubId, ageGroupId, query, list.opponents.size)
                    list = page.copy(opponents = list.opponents + page.opponents)
                }
                _state.value = OpponentsUiState.Idle(list, searched = query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed search keeps the list on screen; only a first load shows the error.
                if (_state.value !is OpponentsUiState.Idle) _state.value = OpponentsUiState.Error
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
