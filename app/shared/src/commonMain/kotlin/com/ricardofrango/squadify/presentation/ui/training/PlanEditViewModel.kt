package com.ricardofrango.squadify.presentation.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetTrainingPlansUseCase
import com.ricardofrango.squadify.domain.usecase.SaveTrainingPlanUseCase
import com.ricardofrango.squadify.model.TrainingPlanLevel
import com.ricardofrango.squadify.model.TrainingPlans
import com.ricardofrango.squadify.model.planFor
import com.ricardofrango.squadify.presentation.errorMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_plan_save_failed

data class PlanEditUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val plans: TrainingPlans? = null,
    val periodStart: LocalDate? = null,
    val form: PlanForm = PlanForm(),
    // The form as loaded, so leaving with changes asks first.
    val original: PlanForm = PlanForm(),
    val saving: Boolean = false,
    // Localized copy only (AI_GUIDELINES.md).
    val error: String? = null
) {
    val dirty: Boolean get() = form != original
}

/**
 * The plan editors (design 40c/d/e) for one [TrainingPlanLevel]: loads the age group's plans, edits one
 * period's plan, and moves to the previous / next period (month and week editors) without leaving.
 */
class PlanEditViewModel(
    private val getTrainingPlansUseCase: GetTrainingPlansUseCase,
    private val saveTrainingPlanUseCase: SaveTrainingPlanUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(PlanEditUiState())
    val state: StateFlow<PlanEditUiState> = _state.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    private var ageGroupId: String? = null
    private var level: TrainingPlanLevel = TrainingPlanLevel.SEASON

    fun start(ageGroupId: String, level: TrainingPlanLevel, periodStart: LocalDate?, force: Boolean = false) {
        if (!force && this.ageGroupId != null) return
        this.ageGroupId = ageGroupId
        this.level = level
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            val plans = try {
                getTrainingPlansUseCase(ageGroupId)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true) }
                return@launch
            }
            val start = if (level == TrainingPlanLevel.SEASON) plans.season.startDate else periodStart ?: plans.season.startDate
            val form = plans.plans.planFor(level, start).toForm()
            _state.update { PlanEditUiState(loading = false, plans = plans, periodStart = start, form = form, original = form) }
        }
    }

    /** Prev / Next month or week: the screen asks about unsaved changes before calling this. */
    fun moveTo(periodStart: LocalDate) = _state.update { s ->
        val form = s.plans?.plans?.planFor(level, periodStart).toForm()
        s.copy(periodStart = periodStart, form = form, original = form, error = null)
    }

    fun onFormChange(form: PlanForm) = _state.update { it.copy(form = form, error = null) }

    fun save() {
        val s = _state.value
        val ageGroupId = ageGroupId ?: return
        val plans = s.plans ?: return
        val periodStart = s.periodStart ?: return
        if (s.saving || !s.form.isValid(level, plans.season)) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val saved = saveTrainingPlanUseCase(ageGroupId, s.form.toRequest(level, periodStart))
                _state.update {
                    it.copy(
                        saving = false,
                        original = it.form,
                        plans = plans.copy(plans = plans.plans.filterNot { p -> p.level == level && p.periodStart == periodStart } + saved)
                    )
                }
                _saved.emit(Unit)
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_plan_save_failed)
                _state.update { it.copy(saving = false, error = message) }
            }
        }
    }
}
