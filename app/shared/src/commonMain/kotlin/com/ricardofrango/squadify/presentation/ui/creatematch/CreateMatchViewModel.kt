package com.ricardofrango.squadify.presentation.ui.creatematch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.CreateMatchUseCase
import com.ricardofrango.squadify.domain.usecase.CreateOpponentUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetOpponentsUseCase
import com.ricardofrango.squadify.domain.usecase.GetTeamDetailsUseCase
import com.ricardofrango.squadify.model.CreateMatchRequest
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.presentation.errorMessage
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_create_match_failed
import squadify.app.shared.generated.resources.msg_failed_to_load_match_form
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock
import kotlin.time.Instant

class CreateMatchViewModel(
    private val getAgeGroupDetailsUseCase: GetAgeGroupDetailsUseCase,
    private val getTeamDetailsUseCase: GetTeamDetailsUseCase,
    private val getOpponentsUseCase: GetOpponentsUseCase,
    private val createOpponentUseCase: CreateOpponentUseCase,
    private val createMatchUseCase: CreateMatchUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<CreateMatchUiState>(CreateMatchUiState.Loading)
    val state: StateFlow<CreateMatchUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<CreateMatchNavigationEvent>()
    val navigationEvent: SharedFlow<CreateMatchNavigationEvent> = _navigationEvent.asSharedFlow()

    private data class Context(
        val clubId: String,
        val clubName: String,
        val ageGroupId: String,
        val ageGroupName: String,
        val seasonName: String,
        val teams: List<Team>,
        val teamFixed: Boolean
    )

    fun load(ageGroupId: String?, teamId: String?) {
        if (_state.value is CreateMatchUiState.Idle) return

        viewModelScope.launch {
            _state.update { CreateMatchUiState.Loading }
            try {
                val ctx = when {
                    teamId != null -> getTeamDetailsUseCase(teamId).let { d ->
                        Context(d.club.id, d.club.name, d.ageGroup.id, d.ageGroup.name, d.season.name, listOf(d.team), teamFixed = true)
                    }
                    ageGroupId != null -> getAgeGroupDetailsUseCase(ageGroupId).let { d ->
                        Context(d.club.id, d.club.name, d.ageGroup.id, d.ageGroup.name, d.season.name, d.teams, teamFixed = false)
                    }
                    else -> {
                        _state.update { CreateMatchUiState.Error(getString(Res.string.msg_failed_to_load_match_form)) }
                        return@launch
                    }
                }
                val opponents = try {
                    getOpponentsUseCase(ctx.clubId, ctx.ageGroupId)
                } catch (e: Exception) {
                    emptyList()
                }
                _state.update {
                    CreateMatchUiState.Idle(
                        clubId = ctx.clubId,
                        clubName = ctx.clubName,
                        ageGroupId = ctx.ageGroupId,
                        ageGroupName = ctx.ageGroupName,
                        seasonName = ctx.seasonName,
                        teamFixed = ctx.teamFixed,
                        teams = ctx.teams,
                        selectedTeamId = if (ctx.teamFixed || ctx.teams.size == 1) ctx.teams.firstOrNull()?.id else null,
                        opponents = opponents,
                        date = nextSaturday(),
                        time = LocalTime(10, 30)
                    )
                }
            } catch (e: Exception) {
                _state.update { CreateMatchUiState.Error(getString(Res.string.msg_failed_to_load_match_form)) }
            }
        }
    }

    private inline fun updateIdle(block: (CreateMatchUiState.Idle) -> CreateMatchUiState.Idle) {
        _state.update { if (it is CreateMatchUiState.Idle) block(it) else it }
    }

    fun onTeamSelected(teamId: String) = updateIdle { it.copy(selectedTeamId = teamId, error = null) }

    fun onOpponentSelected(opponentId: String) =
        updateIdle { it.copy(selectedOpponentId = opponentId, addingOpponent = false, newOpponentName = "", error = null) }

    fun onStartAddOpponent() = updateIdle { it.copy(addingOpponent = true, selectedOpponentId = null, error = null) }

    fun onCancelAddOpponent() = updateIdle { it.copy(addingOpponent = false, newOpponentName = "", error = null) }

    fun onNewOpponentNameChange(name: String) = updateIdle { it.copy(newOpponentName = name, error = null) }

    fun onMatchTypeSelected(type: MatchType) = updateIdle { it.copy(matchType = type, matchTypeError = false, error = null) }

    fun onHalvesSelected(halves: Int) = updateIdle { it.copy(halves = halves) }

    fun onDateChange(date: LocalDate) = updateIdle { it.copy(date = date) }

    fun onTimeChange(time: LocalTime) = updateIdle { it.copy(time = time) }

    fun onLocationChange(location: String) = updateIdle { it.copy(location = location) }

    fun onNotesChange(notes: String) = updateIdle { it.copy(notes = notes) }

    fun clearError() = updateIdle { it.copy(error = null) }

    fun onSubmit() {
        val current = _state.value as? CreateMatchUiState.Idle ?: return
        val teamId = current.selectedTeamId ?: return
        val matchType = current.matchType ?: run {
            updateIdle { it.copy(matchTypeError = true) }
            return
        }
        if (!current.canSubmit) return

        viewModelScope.launch {
            _state.update { current.copy(isSubmitting = true, error = null) }
            try {
                val opponentId = current.selectedOpponentId
                    ?: createOpponentUseCase(
                        current.clubId,
                        CreateOpponentRequest(name = current.newOpponentName.trim(), ageGroupId = current.ageGroupId)
                    ).id

                val dateTime: Instant = current.date
                    .atTime(current.time)
                    .toInstant(TimeZone.currentSystemDefault())

                createMatchUseCase(
                    CreateMatchRequest(
                        teamId = teamId,
                        opponentId = opponentId,
                        location = current.location.trim().ifBlank { null },
                        dateTime = dateTime,
                        notes = current.notes.trim().ifBlank { null },
                        type = matchType,
                        halves = current.halves
                    )
                )
                _navigationEvent.emit(CreateMatchNavigationEvent.NavigateBackWithMatchCreated)
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_create_match_failed)
                _state.update {
                    (it as? CreateMatchUiState.Idle)?.copy(isSubmitting = false, error = message) ?: it
                }
            }
        }
    }

    /** The coming Saturday (today, if today is Saturday). */
    private fun nextSaturday(): LocalDate {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val daysAhead = (DayOfWeek.SATURDAY.isoDayNumber - today.dayOfWeek.isoDayNumber + 7) % 7
        return today.plus(daysAhead, DateTimeUnit.DAY)
    }
}
