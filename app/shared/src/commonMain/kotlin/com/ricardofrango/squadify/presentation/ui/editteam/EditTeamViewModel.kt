package com.ricardofrango.squadify.presentation.ui.editteam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetTeamDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateTeamUseCase
import com.ricardofrango.squadify.model.UpdateTeamRequest
import com.ricardofrango.squadify.presentation.errorMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.edit_team_failed
import squadify.app.shared.generated.resources.edit_team_load_failed

/** Data class form state for Edit team: the name and the team size (one of the club sport's sizes). */
data class TeamFormFields(val name: String, val teamSize: Int?)

sealed class EditTeamUiState {
    data object Loading : EditTeamUiState()
    data class Error(val message: String) : EditTeamUiState()

    data class Idle(
        val ageGroupName: String,
        val seasonName: String,
        val teamSizeOptions: List<Int>,
        val original: TeamFormFields,
        val form: TeamFormFields,
        val isSubmitting: Boolean = false,
        val submitError: String? = null
    ) : EditTeamUiState() {
        val isDirty: Boolean get() = form.name.trim() != original.name || form.teamSize != original.teamSize
        val canSave: Boolean get() = isDirty && form.name.isNotBlank() && !isSubmitting

        /** Only the fields that changed, so an untouched one is never overwritten. */
        fun toRequest() = UpdateTeamRequest(
            name = form.name.trim().takeIf { it != original.name },
            teamSize = form.teamSize.takeIf { it != original.teamSize }
        )
    }
}

class EditTeamViewModel(
    private val getTeamDetailsUseCase: GetTeamDetailsUseCase,
    private val updateTeamUseCase: UpdateTeamUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<EditTeamUiState>(EditTeamUiState.Loading)
    val state: StateFlow<EditTeamUiState> = _state.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    fun load(teamId: String) {
        viewModelScope.launch {
            _state.value = EditTeamUiState.Loading
            _state.value = try {
                val details = getTeamDetailsUseCase(teamId)
                val fields = TeamFormFields(details.team.name, details.team.teamSize)
                EditTeamUiState.Idle(
                    ageGroupName = details.ageGroup.name,
                    seasonName = details.season.name,
                    teamSizeOptions = details.club.sport.teamSizes,
                    original = fields,
                    form = fields
                )
            } catch (e: Exception) {
                EditTeamUiState.Error(errorMessage(e, Res.string.edit_team_load_failed))
            }
        }
    }

    fun updateForm(transform: (TeamFormFields) -> TeamFormFields) {
        _state.update { (it as? EditTeamUiState.Idle)?.copy(form = transform(it.form), submitError = null) ?: it }
    }

    fun save(teamId: String) {
        val current = _state.value as? EditTeamUiState.Idle ?: return
        if (!current.canSave) return
        _state.value = current.copy(isSubmitting = true, submitError = null)
        viewModelScope.launch {
            try {
                updateTeamUseCase(teamId, current.toRequest())
                _saved.emit(Unit)
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.edit_team_failed)
                _state.update { (it as? EditTeamUiState.Idle)?.copy(isSubmitting = false, submitError = message) ?: it }
            }
        }
    }
}
