package com.ricardofrango.squadify.presentation.ui.addstafftoagegroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.AddStaffToAgeGroupUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupStaffUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupsUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubMembersUseCase
import com.ricardofrango.squadify.domain.usecase.GetSeasonsUseCase
import com.ricardofrango.squadify.model.AddStaffToAgeGroup
import com.ricardofrango.squadify.model.AddStaffToAgeGroupRequest
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.presentation.errorMessage
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_add_staff_failed
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddStaffToAgeGroupViewModel(
    private val getAgeGroupsUseCase: GetAgeGroupsUseCase,
    private val getSeasonsUseCase: GetSeasonsUseCase,
    private val getClubMembersUseCase: GetClubMembersUseCase,
    private val getAgeGroupStaffUseCase: GetAgeGroupStaffUseCase,
    private val addStaffToAgeGroupUseCase: AddStaffToAgeGroupUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<AddStaffToAgeGroupUiState>(AddStaffToAgeGroupUiState.Loading)
    val state: StateFlow<AddStaffToAgeGroupUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<AddStaffToAgeGroupNavigationEvent>()
    val navigationEvent: SharedFlow<AddStaffToAgeGroupNavigationEvent> = _navigationEvent.asSharedFlow()

    private var clubId: String = ""

    fun load(clubId: String, initialAgeGroupId: String? = null) {
        this.clubId = clubId
        if (_state.value is AddStaffToAgeGroupUiState.Idle) return

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
            // recently ending season (tiebreak oldest players) -- mirrors AddPlayersToAgeGroupViewModel.
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
                AddStaffToAgeGroupUiState.Idle(
                    ageGroups = ageGroups,
                    seasons = seasons,
                    selectedAgeGroupId = selectedAgeGroupId
                )
            }
            if (selectedAgeGroupId != null) loadEligible(clubId, selectedAgeGroupId)
        }
    }

    fun onAgeGroupSelected(clubId: String, ageGroupId: String) {
        val current = _state.value as? AddStaffToAgeGroupUiState.Idle ?: return
        if (ageGroupId == current.selectedAgeGroupId) return
        _state.update {
            current.copy(
                selectedAgeGroupId = ageGroupId,
                selectedRoles = emptyMap(),
                eligibleMembers = emptyList(),
                loadingEligible = true,
                error = null
            )
        }
        loadEligible(clubId, ageGroupId)
    }

    private fun loadEligible(clubId: String, ageGroupId: String) {
        viewModelScope.launch {
            val members = try {
                getClubMembersUseCase(clubId)
            } catch (e: Exception) {
                emptyList()
            }
            val existingStaffIds = try {
                getAgeGroupStaffUseCase(ageGroupId).map { it.userId }.toSet()
            } catch (e: Exception) {
                emptySet()
            }
            val eligible = members.filterNot { it.userId in existingStaffIds }
            _state.update { state ->
                if (state is AddStaffToAgeGroupUiState.Idle && state.selectedAgeGroupId == ageGroupId) {
                    state.copy(eligibleMembers = eligible, loadingEligible = false)
                } else {
                    state
                }
            }
        }
    }

    /** Re-fetch the eligible list for the currently selected age group, keeping the current
     * selection -- called when the user comes back from registering new staff via the "New staff
     * member" shortcut, so those members appear without leaving the screen. */
    fun refreshEligibleMembers() {
        val current = _state.value as? AddStaffToAgeGroupUiState.Idle ?: return
        val ageGroupId = current.selectedAgeGroupId ?: return
        _state.update { current.copy(loadingEligible = true, error = null) }
        loadEligible(clubId, ageGroupId)
    }

    /** Tapping a role pill selects the member with that role; tapping the already-selected role
     * deselects them. */
    fun onRoleTapped(userId: String, role: AgeGroupRole) {
        _state.update { state ->
            if (state is AddStaffToAgeGroupUiState.Idle) {
                val next = state.selectedRoles.toMutableMap()
                if (next[userId] == role) next.remove(userId) else next[userId] = role
                state.copy(selectedRoles = next, error = null)
            } else {
                state
            }
        }
    }

    fun clearError() {
        _state.update { state ->
            if (state is AddStaffToAgeGroupUiState.Idle) state.copy(error = null) else state
        }
    }

    fun onAddClicked() {
        val current = _state.value as? AddStaffToAgeGroupUiState.Idle ?: return
        val ageGroupId = current.selectedAgeGroupId ?: return
        if (current.selectedRoles.isEmpty()) return

        viewModelScope.launch {
            _state.update { current.copy(isSubmitting = true, error = null) }
            try {
                val staff = current.selectedRoles.map { (userId, role) -> AddStaffToAgeGroup(userId, role) }
                addStaffToAgeGroupUseCase(ageGroupId, AddStaffToAgeGroupRequest(staff))
                _navigationEvent.emit(AddStaffToAgeGroupNavigationEvent.NavigateBackWithStaffAdded)
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_add_staff_failed)
                _state.update { current.copy(isSubmitting = false, error = message) }
            }
        }
    }
}
