package com.ricardofrango.squadify.presentation.ui.editagegroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupsUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateAgeGroupUseCase
import com.ricardofrango.squadify.model.UpdateAgeGroupRequest
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
import squadify.app.shared.generated.resources.edit_age_group_failed
import squadify.app.shared.generated.resources.edit_age_group_load_failed

/** The editable fields. A single-year group has [startYear] == [endYear]. */
data class AgeGroupFormFields(
    val name: String,
    val twoYears: Boolean,
    val startYear: Int,
    val endYear: Int,
    val teamSize: Int?
)

sealed class EditAgeGroupUiState {
    data object Loading : EditAgeGroupUiState()
    data class Error(val message: String) : EditAgeGroupUiState()

    data class Idle(
        val seasonName: String,
        val teamSizeOptions: List<Int>,
        // Birth years taken by the season's other age groups -- left out of the pickers, so a
        // duplicate span can't be picked (the server rejects one too).
        val usedByOthers: Set<Int>,
        // Birth years of the players in the pool: the new oldest year can't shut any of them out.
        val poolBirthYears: List<Int>,
        val original: AgeGroupFormFields,
        val form: AgeGroupFormFields,
        val isSubmitting: Boolean = false,
        val submitError: String? = null
    ) : EditAgeGroupUiState() {
        val inverted: Boolean get() = form.twoYears && form.startYear < form.endYear

        /** The span shares a birth year with another age group of the season (e.g. after switching to two years). */
        val collides: Boolean get() = (form.endYear..form.startYear).any { it in usedByOthers }

        /** Players the new span would leave ineligible (born before the new oldest year). */
        val shutOut: Int get() = poolBirthYears.count { it < form.endYear }

        val isDirty: Boolean get() = form.copy(name = form.name.trim()) != original
        val canSave: Boolean get() = isDirty && form.name.isNotBlank() && !inverted && !collides && shutOut == 0 && !isSubmitting

        fun toRequest(): UpdateAgeGroupRequest {
            val spanChanged = form.startYear != original.startYear || form.endYear != original.endYear
            return UpdateAgeGroupRequest(
                name = form.name.trim().takeIf { it != original.name },
                startYear = form.startYear.takeIf { spanChanged },
                endYear = form.endYear.takeIf { spanChanged },
                teamSize = form.teamSize.takeIf { it != original.teamSize }
            )
        }
    }
}

class EditAgeGroupViewModel(
    private val getAgeGroupDetailsUseCase: GetAgeGroupDetailsUseCase,
    private val getAgeGroupsUseCase: GetAgeGroupsUseCase,
    private val updateAgeGroupUseCase: UpdateAgeGroupUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<EditAgeGroupUiState>(EditAgeGroupUiState.Loading)
    val state: StateFlow<EditAgeGroupUiState> = _state.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    fun load(ageGroupId: String) {
        viewModelScope.launch {
            _state.value = EditAgeGroupUiState.Loading
            _state.value = try {
                val details = getAgeGroupDetailsUseCase(ageGroupId)
                val ag = details.ageGroup
                val others = getAgeGroupsUseCase(ag.clubId).filter { it.seasonId == ag.seasonId && it.id != ag.id }
                val fields = AgeGroupFormFields(ag.name, ag.startYear != ag.endYear, ag.startYear, ag.endYear, ag.teamSize)
                EditAgeGroupUiState.Idle(
                    seasonName = details.season.name,
                    teamSizeOptions = details.club.sport.teamSizes,
                    usedByOthers = others.flatMap { it.endYear..it.startYear }.toSet(),
                    poolBirthYears = details.players.map { it.birthDate.year },
                    original = fields,
                    form = fields
                )
            } catch (e: Exception) {
                EditAgeGroupUiState.Error(errorMessage(e, Res.string.edit_age_group_load_failed))
            }
        }
    }

    fun updateForm(transform: (AgeGroupFormFields) -> AgeGroupFormFields) {
        _state.update { (it as? EditAgeGroupUiState.Idle)?.copy(form = transform(it.form), submitError = null) ?: it }
    }

    fun save(ageGroupId: String) {
        val current = _state.value as? EditAgeGroupUiState.Idle ?: return
        if (!current.canSave) return
        _state.value = current.copy(isSubmitting = true, submitError = null)
        viewModelScope.launch {
            try {
                updateAgeGroupUseCase(ageGroupId, current.toRequest())
                _saved.emit(Unit)
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.edit_age_group_failed)
                _state.update { (it as? EditAgeGroupUiState.Idle)?.copy(isSubmitting = false, submitError = message) ?: it }
            }
        }
    }
}
