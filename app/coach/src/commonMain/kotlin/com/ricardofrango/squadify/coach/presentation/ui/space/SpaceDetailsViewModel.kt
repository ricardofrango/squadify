package com.ricardofrango.squadify.coach.presentation.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.coach.domain.GetSpaceDetailsUseCase
import com.ricardofrango.squadify.model.SpaceAgeGroup
import com.ricardofrango.squadify.model.SpaceDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private val getSpaceDetailsUseCase: GetSpaceDetailsUseCase
) : ViewModel() {
    private val _state = MutableStateFlow<SpaceDetailsUiState>(SpaceDetailsUiState.Loading)
    val state: StateFlow<SpaceDetailsUiState> = _state.asStateFlow()

    private var spaceId: String? = null

    /** Loads [spaceId]'s details, once; [force] again (after a change, or to retry). */
    fun load(spaceId: String, force: Boolean = false) {
        if (!force && this.spaceId == spaceId && _state.value is SpaceDetailsUiState.Loaded) return
        this.spaceId = spaceId
        viewModelScope.launch {
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
}
