package com.sportssoftw.squadify.coach.presentation.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportssoftw.squadify.coach.domain.CheckAgeGroupRoomUseCase
import com.sportssoftw.squadify.coach.domain.GetSpaceDetailsUseCase
import com.sportssoftw.squadify.data.cache.ForceRefresh
import com.sportssoftw.squadify.presentation.refresh.RefreshFlag
import com.sportssoftw.squadify.model.SpaceAgeGroup
import com.sportssoftw.squadify.model.SpaceDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

sealed class SpaceDetailsUiState {
    data object Loading : SpaceDetailsUiState()
    data object Failed : SpaceDetailsUiState()

    /** The space, its age groups sorted by club, then youngest first ([sortedAgeGroups]). */
    data class Loaded(val details: SpaceDetails) : SpaceDetailsUiState() {
        val ageGroups: List<SpaceAgeGroup> = sortedAgeGroups(details.ageGroups)
    }
}

/**
 * The space's age groups as its details list them: by the club each plays for (its name's part before
 * " - "), then by age, the youngest first (Sub 9 before Sub 11); one with no club name after the rest.
 */
fun sortedAgeGroups(ageGroups: List<SpaceAgeGroup>): List<SpaceAgeGroup> =
    ageGroups.sortedWith(
        compareBy<SpaceAgeGroup>({ clubNameOf(it.ageGroup) == null }, { clubNameOf(it.ageGroup)?.lowercase() })
            .thenByDescending { it.ageGroup.endYear }
            .thenBy { it.ageGroup.name.lowercase() }
    )

/** One space's details (the coach app): its age groups, as `GET /v1/spaces/{id}` has them. */
class SpaceDetailsViewModel(
    private val getSpaceDetailsUseCase: GetSpaceDetailsUseCase,
    private val checkAgeGroupRoomUseCase: CheckAgeGroupRoomUseCase
) : ViewModel() {
    /** Asks the coach server for room for one more age group before the wizard or the import opens; throws its refusal. */
    suspend fun checkAgeGroupRoom(spaceId: String) = checkAgeGroupRoomUseCase(spaceId)

    private val _state = MutableStateFlow<SpaceDetailsUiState>(SpaceDetailsUiState.Loading)
    val state: StateFlow<SpaceDetailsUiState> = _state.asStateFlow()

    private var spaceId: String? = null

    private val refreshFlag = RefreshFlag()

    /** Up while a pull-to-refresh reloads the details; the content stays on screen meanwhile. */
    val isRefreshing: StateFlow<Boolean> = refreshFlag.running

    /** Loads [spaceId]'s details, once; [force] again (after a change, or to retry). A forced load reads the server. */
    fun load(spaceId: String, force: Boolean = false) {
        if (!force && this.spaceId == spaceId && _state.value is SpaceDetailsUiState.Loaded) return
        this.spaceId = spaceId
        viewModelScope.launch(if (force) ForceRefresh else EmptyCoroutineContext) { fetch(spaceId) }
    }

    /** A pull-to-refresh: the server, not the cache; the content stays on screen and the indicator shows. */
    fun refreshContent() {
        val id = spaceId ?: return
        viewModelScope.launch(ForceRefresh) { refreshFlag.during { fetch(id) } }
    }

    private suspend fun fetch(spaceId: String) {
        if (_state.value !is SpaceDetailsUiState.Loaded) _state.value = SpaceDetailsUiState.Loading
        _state.value = try {
            SpaceDetailsUiState.Loaded(getSpaceDetailsUseCase(spaceId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_state.value is SpaceDetailsUiState.Loaded) _state.value else SpaceDetailsUiState.Failed
        }
    }
}
