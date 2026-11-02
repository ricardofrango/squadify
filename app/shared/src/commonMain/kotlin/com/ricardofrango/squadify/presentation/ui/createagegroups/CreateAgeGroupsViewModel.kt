package com.ricardofrango.squadify.presentation.ui.createagegroups

import com.ricardofrango.squadify.data.remote.isNotInPlan
import com.ricardofrango.squadify.domain.SeasonDateDefaults
import com.ricardofrango.squadify.domain.config.AppFeatures
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.domain.usecase.CreateAgeGroupsUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupsUseCase
import com.ricardofrango.squadify.domain.usecase.GetSeasonsUseCase
import com.ricardofrango.squadify.model.AgeGroupItemRequest
import com.ricardofrango.squadify.model.CreateAgeGroupsRequest
import com.ricardofrango.squadify.presentation.errorMessage
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_age_group_conflict
import squadify.app.shared.generated.resources.msg_age_group_conflict_rows
import squadify.app.shared.generated.resources.msg_age_groups_not_in_plan
import squadify.app.shared.generated.resources.msg_create_age_groups_failed
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class CreateAgeGroupsViewModel(
    private val createAgeGroupsUseCase: CreateAgeGroupsUseCase,
    private val getAgeGroupsUseCase: GetAgeGroupsUseCase,
    private val getSeasonsUseCase: GetSeasonsUseCase,
    private val features: AppFeatures
) : ViewModel() {

    private val _state = MutableStateFlow<CreateAgeGroupsUiState>(CreateAgeGroupsUiState.Loading)
    val state: StateFlow<CreateAgeGroupsUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<CreateAgeGroupsNavigationEvent>()
    val navigationEvent: SharedFlow<CreateAgeGroupsNavigationEvent> = _navigationEvent.asSharedFlow()

    fun load(clubId: String, initialSeasonId: String?) {
        if (_state.value is CreateAgeGroupsUiState.Idle) return

        viewModelScope.launch {
            // Without seasons to pick (the coach app), the season the server picks, shown and not sent.
            val seasons = if (!features.seasons) {
                listOf(SeasonDateDefaults.automaticSeason(clubId, Clock.System.todayIn(TimeZone.currentSystemDefault())))
            } else try {
                getSeasonsUseCase(clubId)
            } catch (e: Exception) {
                emptyList()
            }
            val ageGroups = try {
                getAgeGroupsUseCase(clubId)
            } catch (e: Exception) {
                // The form is still usable without this -- it only pre-empts a conflict the server
                // would otherwise reject, it doesn't gate submission.
                emptyList()
            }
            // Honour the caller's pre-selection when it points at a real season; otherwise start on
            // the most recently started one so the form isn't blank. Either way the picker can change
            // it -- from the overflow menu the caller passes null, so this just seeds a sensible
            // default the user can override.
            val selectedSeasonId = initialSeasonId?.takeIf { id -> seasons.any { it.id == id } }
                ?: seasons.maxByOrNull { it.startDate }?.id
            _state.update {
                CreateAgeGroupsUiState.Idle(
                    pickSeason = features.seasons,
                    seasons = seasons,
                    selectedSeasonId = selectedSeasonId,
                    ageGroups = ageGroups
                )
            }
        }
    }

    fun onSeasonSelected(seasonId: String) {
        _state.update { state ->
            if (state is CreateAgeGroupsUiState.Idle) state.copy(selectedSeasonId = seasonId, error = null, conflictRows = emptySet()) else state
        }
    }

    fun clearSubmitError() {
        _state.update { state ->
            if (state is CreateAgeGroupsUiState.Idle) state.copy(error = null, conflictRows = emptySet()) else state
        }
    }

    fun onCreateClicked(clubId: String, items: List<AgeGroupItemRequest>) {
        val current = _state.value as? CreateAgeGroupsUiState.Idle ?: return
        val seasonId = current.selectedSeasonId ?: return

        viewModelScope.launch {
            _state.update { current.copy(isSubmitting = true, error = null, conflictRows = emptySet()) }
            try {
                createAgeGroupsUseCase(CreateAgeGroupsRequest(clubId, seasonId.takeIf { current.pickSeason }, items))
                _navigationEvent.emit(CreateAgeGroupsNavigationEvent.NavigateBackWithAgeGroupsCreated)
            } catch (e: Exception) {
                // The server reports every clashing row (ApiException.rows): they're marked, and named
                // in the message. An older server without them gets the general message.
                val rows = (e as? ApiException)?.takeIf { it.statusCode == 409 }?.rows.orEmpty()
                val message = when {
                    rows.isNotEmpty() -> getString(Res.string.msg_age_group_conflict_rows, rows.joinToString(", ") { (it + 1).toString() })
                    e is ApiException && e.statusCode == 409 -> getString(Res.string.msg_age_group_conflict)
                    // The coach app's plans: no room for more age groups this season.
                    e.isNotInPlan -> getString(Res.string.msg_age_groups_not_in_plan)
                    else -> errorMessage(e, Res.string.msg_create_age_groups_failed)
                }
                _state.update { current.copy(isSubmitting = false, error = message, conflictRows = rows.toSet()) }
            }
        }
    }
}
