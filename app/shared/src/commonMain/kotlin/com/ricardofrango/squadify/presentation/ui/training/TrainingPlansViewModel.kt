package com.ricardofrango.squadify.presentation.ui.training

import com.ricardofrango.squadify.presentation.refresh.RefreshFlag

import com.ricardofrango.squadify.data.cache.ForceRefresh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetTrainingPlansUseCase
import com.ricardofrango.squadify.domain.usecase.GetTrainingSessionsUseCase
import com.ricardofrango.squadify.model.TrainingSessions
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
    private val getTrainingPlansUseCase: GetTrainingPlansUseCase,
    private val getTrainingSessionsUseCase: GetTrainingSessionsUseCase
) : ViewModel() {

    // The week plan lists that week's sessions (design 40e); best effort.
    private val _sessions = MutableStateFlow<TrainingSessions?>(null)
    val sessions: StateFlow<TrainingSessions?> = _sessions.asStateFlow()

    private val _state = MutableStateFlow<TrainingPlansUiState>(TrainingPlansUiState.Loading)
    val state: StateFlow<TrainingPlansUiState> = _state.asStateFlow()

    private val refreshFlag = RefreshFlag()

    /** Up while a pull-to-refresh reads the plans and sessions again; what's shown stays meanwhile. */
    val isRefreshing: StateFlow<Boolean> = refreshFlag.running

    private var ageGroup: String? = null

    /** [force] reloads after an editor saved, keeping what's shown meanwhile. */
    fun load(ageGroupId: String, force: Boolean = false) {
        if (!force && _state.value is TrainingPlansUiState.Idle) return
        ageGroup = ageGroupId
        viewModelScope.launch { fetchSessions(ageGroupId) }
        viewModelScope.launch { fetchPlans(ageGroupId) }
    }

    /** A pull-to-refresh: the server, not the cache, for the plans and the week's sessions. */
    fun refreshContent() {
        val id = ageGroup ?: return
        viewModelScope.launch(ForceRefresh) {
            refreshFlag.during {
                fetchSessions(id)
                fetchPlans(id)
            }
        }
    }

    private suspend fun fetchSessions(ageGroupId: String) {
        _sessions.value = runCatching { getTrainingSessionsUseCase(ageGroupId) }.getOrNull() ?: _sessions.value
    }

    private suspend fun fetchPlans(ageGroupId: String) {
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
