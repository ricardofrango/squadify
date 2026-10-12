package com.ricardofrango.squadify.presentation.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetTrainingPlansUseCase
import com.ricardofrango.squadify.model.TrainingPlans
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class TrainingPlansUiState {
    data object Loading : TrainingPlansUiState()
    data object Error : TrainingPlansUiState()
    data class Idle(val plans: TrainingPlans) : TrainingPlansUiState()
}

/** The season / month / week plan screens (design 40c/d/e, view side): all the age group's plans. */
class TrainingPlansViewModel(
    private val getTrainingPlansUseCase: GetTrainingPlansUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<TrainingPlansUiState>(TrainingPlansUiState.Loading)
    val state: StateFlow<TrainingPlansUiState> = _state.asStateFlow()

    /** [force] reloads after an editor saved, keeping what's shown meanwhile. */
    fun load(ageGroupId: String, force: Boolean = false) {
        if (!force && _state.value is TrainingPlansUiState.Idle) return
        viewModelScope.launch {
            val previous = _state.value as? TrainingPlansUiState.Idle
            if (previous == null) _state.update { TrainingPlansUiState.Loading }
            _state.update {
                try {
                    TrainingPlansUiState.Idle(getTrainingPlansUseCase(ageGroupId))
                } catch (e: Exception) {
                    previous ?: TrainingPlansUiState.Error
                }
            }
        }
    }
}
