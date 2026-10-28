package com.ricardofrango.squadify.presentation.ui.creatematch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.presentation.ui.components.venueLocation
import com.ricardofrango.squadify.model.MatchVenue
import com.ricardofrango.squadify.domain.usecase.CreateMatchUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetOpponentsUseCase
import com.ricardofrango.squadify.domain.usecase.GetTeamDetailsUseCase
import com.ricardofrango.squadify.model.CreateMatchRequest
import com.ricardofrango.squadify.model.MatchType
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
        val teamFixed: Boolean,
        val clubLocation: String?,
        val teamSizes: List<Int>
    )

    fun load(ageGroupId: String?, teamId: String?) {
        if (_state.value is CreateMatchUiState.Idle) return

        viewModelScope.launch {
            _state.update { CreateMatchUiState.Loading }
            try {
                val ctx = when {
                    teamId != null -> getTeamDetailsUseCase(teamId).let { d ->
                        Context(d.club.id, d.club.name, d.ageGroup.id, d.ageGroup.name, d.season.name, listOf(d.team), teamFixed = true, clubLocation = d.club.stadiumLocation ?: d.club.address, teamSizes = d.club.sport.teamSizes)
                    }
                    ageGroupId != null -> getAgeGroupDetailsUseCase(ageGroupId).let { d ->
                        Context(d.club.id, d.club.name, d.ageGroup.id, d.ageGroup.name, d.season.name, d.teams, teamFixed = false, clubLocation = d.club.stadiumLocation ?: d.club.address, teamSizes = d.club.sport.teamSizes)
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
                val selected = if (ctx.teamFixed || ctx.teams.size == 1) ctx.teams.firstOrNull() else null
                _state.update {
                    CreateMatchUiState.Idle(
                        clubId = ctx.clubId,
                        clubName = ctx.clubName,
                        ageGroupId = ctx.ageGroupId,
                        ageGroupName = ctx.ageGroupName,
                        seasonName = ctx.seasonName,
                        teamFixed = ctx.teamFixed,
                        teams = ctx.teams,
                        selectedTeamId = selected?.id,
                        teamSizeOptions = ctx.teamSizes,
                        teamSize = selected?.let { it.teamSize ?: 11 },
                        opponents = opponents,
                        clubLocation = ctx.clubLocation,
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

    // Another team starts from its own count.
    fun onTeamSelected(teamId: String) = updateIdle { state ->
        state.copy(selectedTeamId = teamId, teamSize = state.teams.find { it.id == teamId }?.let { it.teamSize ?: 11 }, error = null)
    }

    fun onTeamSizeSelected(teamSize: Int) = updateIdle { it.copy(teamSize = teamSize) }

    /**
     * The opponent picked on the Opponents screen. One created there is not in [CreateMatchUiState.Idle.opponents]
     * yet, so the list is fetched again to show its name.
     */
    fun onOpponentPicked(opponentId: String) {
        val current = _state.value as? CreateMatchUiState.Idle ?: return
        updateIdle { it.copy(selectedOpponentId = opponentId, error = null).withAwayLocation() }
        if (current.opponents.any { it.id == opponentId }) return
        viewModelScope.launch {
            val opponents = try {
                getOpponentsUseCase(current.clubId, current.ageGroupId)
            } catch (e: Exception) {
                return@launch
            }
            updateIdle { it.copy(opponents = opponents).withAwayLocation() }
        }
    }

    /** Picking where it's played prefills the location: the club's ground, the opponent's, or none. */
    fun onVenueSelected(venue: MatchVenue) = updateIdle {
        it.copy(venue = venue, venueError = false, error = null, location = venueLocation(venue, it.clubLocation, it.selectedOpponent))
    }

    // An away match follows the opponent: picking another one moves the location to their ground.
    private fun CreateMatchUiState.Idle.withAwayLocation() =
        if (venue == MatchVenue.AWAY) copy(location = venueLocation(MatchVenue.AWAY, clubLocation, selectedOpponent)) else this

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
        val opponentId = current.selectedOpponentId ?: return
        // A missing type or venue is pointed out (red) rather than silently ignored.
        if (current.matchType == null || current.venue == null) {
            updateIdle { it.copy(matchTypeError = it.matchType == null, venueError = it.venue == null) }
            return
        }
        val matchType = current.matchType
        if (!current.canSubmit) return

        viewModelScope.launch {
            _state.update { current.copy(isSubmitting = true, error = null) }
            try {
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
                        halves = current.halves,
                        venue = current.venue,
                        teamSize = current.teamSize
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
