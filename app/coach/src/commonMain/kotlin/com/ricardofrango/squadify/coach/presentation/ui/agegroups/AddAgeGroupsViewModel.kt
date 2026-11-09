package com.ricardofrango.squadify.coach.presentation.ui.agegroups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.coach.domain.AddAgeGroupsUseCase
import com.ricardofrango.squadify.coach.domain.AgeGroupsAdded
import com.ricardofrango.squadify.coach.domain.GetSpaceDetailsUseCase
import com.ricardofrango.squadify.coach.presentation.ui.home.SpaceClub
import com.ricardofrango.squadify.coach.presentation.ui.space.clubNameOf
import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.domain.usecase.GetCurrentUserUseCase
import com.ricardofrango.squadify.model.AGE_GROUP_CLUB_SEPARATOR
import com.ricardofrango.squadify.model.AddAgeGroupsRequest
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.NewAgeGroup
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.SportType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Why creating didn't work, shown above the buttons. */
enum class WizardProblem { PLAN_LIMIT, YEARS_TAKEN, CREST_TOO_BIG, FAILED }

/**
 * The wizard, one step at a time ([step]: 0 club, 1 age groups, 2 teams). [spaceExists]: the coach's own
 * space is there; otherwise the first age groups make it, named [spaceName], in the [sport] picked on the
 * second step. [existing]: the space's age groups (any season), for the years a club already has
 * ([seasonId]'s) and the clubs used before ([usedClubs]).
 */
data class AddAgeGroupsUiState(
    val loading: Boolean = true,
    val failedToLoad: Boolean = false,
    val step: Int = 0,
    val spaceExists: Boolean = false,
    val spaceName: String? = null,
    val sport: SportType = SportType.FOOTBALL,
    val plan: SpacePlan? = null,
    val seasonId: String? = null,
    val seasonName: String? = null,
    val seasonEndYear: Int,
    val existing: List<AgeGroup> = emptyList(),
    val ageGroupsLeft: Int? = null,
    val clubName: String = "",
    // The picked image, or the path of a crest the space already uses (an earlier age group's).
    val crest: ByteArray? = null,
    val crestPath: String? = null,
    val picked: List<PickedAgeGroup> = emptyList(),
    val showErrors: Boolean = false,
    val submitting: Boolean = false,
    val problem: WizardProblem? = null,
    // The oldest years of the picked age groups the server said the club already has.
    val takenYears: Set<Int> = emptySet()
) {
    val usedClubs: List<SpaceClub>
        get() = existing.mapNotNull { ageGroup -> clubNameOf(ageGroup)?.let { it to ageGroup.logoUrl } }
            .groupBy({ it.first.lowercase() }, { it })
            .map { (_, named) -> SpaceClub(named.first().first, named.firstNotNullOfOrNull { it.second }) }
            .sortedBy { it.name.lowercase() }

    val clubNameValid: Boolean get() = clubName.isNotBlank() && AGE_GROUP_CLUB_SEPARATOR !in clubName.trim()

    /** Whether more age groups can be picked: the plan's room this season, when it has a limit. */
    val canPickMore: Boolean get() = ageGroupsLeft == null || picked.size < ageGroupsLeft

    val teamCount: Int get() = picked.sumOf { it.teams }

    /** Whether the club already has an age group sharing these years this season. */
    fun yearsTaken(oldestYear: Int, startYear: Int = oldestYear): Boolean =
        clubHasYears(existing, seasonId, clubName, startYear, oldestYear)
}

sealed class AddAgeGroupsEvent {
    /** Done: back to [spaceId]'s details, with its new age groups ([ageGroupIds]) and how many [teams]. */
    data class Created(val spaceId: String, val ageGroupIds: List<String>, val teams: Int, val crestSaved: Boolean) : AddAgeGroupsEvent()
}

/**
 * The age-group wizard (design "Assistente: Adicionar escalões"): the club these age groups play for (name and
 * crest), its age groups by birth year in the current season, then how many teams each. Everything is
 * created at once (`POST /v1/space/age-groups`); the crest is given to each afterwards.
 */
class AddAgeGroupsViewModel(
    private val getSpaceDetailsUseCase: GetSpaceDetailsUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val addAgeGroupsUseCase: AddAgeGroupsUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(AddAgeGroupsUiState(seasonEndYear = seasonEndYear(Clock.System.todayIn(TimeZone.currentSystemDefault()))))
    val state: StateFlow<AddAgeGroupsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AddAgeGroupsEvent>()
    val events: SharedFlow<AddAgeGroupsEvent> = _events.asSharedFlow()

    private var loadedFor: String? = null

    /** The coach's own space ([spaceId]), or null when they have none yet: then it's named after them. */
    fun load(spaceId: String?, defaultSpaceName: (String) -> String) {
        val key = spaceId ?: "new"
        if (loadedFor == key && !_state.value.failedToLoad) return
        loadedFor = key
        viewModelScope.launch {
            _state.update { it.copy(loading = true, failedToLoad = false) }
            try {
                if (spaceId != null) {
                    val details = getSpaceDetailsUseCase(spaceId)
                    _state.update {
                        it.copy(
                            loading = false, spaceExists = true, spaceName = details.space.name, sport = details.space.sport,
                            plan = details.plan, seasonId = details.seasonId, seasonName = details.seasonName,
                            existing = details.ageGroups.map { row -> row.ageGroup }, ageGroupsLeft = details.ageGroupsLeft
                        )
                    }
                } else {
                    val user = getCurrentUserUseCase()
                    _state.update { it.copy(loading = false, spaceExists = false, spaceName = defaultSpaceName("${user.firstName} ${user.lastName}".trim())) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, failedToLoad = true) }
            }
        }
    }

    fun onClubNameChange(name: String) = _state.update { it.copy(clubName = name, problem = null, takenYears = emptySet()) }

    fun onCrestPicked(bytes: ByteArray) = _state.update {
        if (bytes.size > MAX_CREST_BYTES) it.copy(problem = WizardProblem.CREST_TOO_BIG)
        else it.copy(crest = bytes, crestPath = null, problem = null)
    }

    /** A club the space already uses: its name and crest. */
    fun onUsedClubPicked(club: SpaceClub) = _state.update { it.copy(clubName = club.name, crest = null, crestPath = club.logoUrl, problem = null, takenYears = emptySet()) }

    fun onSportChange(sport: SportType) = _state.update { state ->
        if (state.spaceExists) state
        else state.copy(sport = sport, picked = state.picked.map { it.copy(teamSize = defaultTeamSize(sport, state.seasonEndYear - it.oldestYear), joinYounger = false) })
    }

    /** Picks or unpicks the age group born in [oldestYear]. */
    fun onYearToggled(oldestYear: Int) = _state.update { state ->
        val picked = state.picked.firstOrNull { it.oldestYear == oldestYear }
        when {
            picked != null -> state.copy(picked = state.picked - picked, problem = null, takenYears = state.takenYears - oldestYear)
            !state.canPickMore || state.yearsTaken(oldestYear) -> state
            else -> state.copy(
                picked = (state.picked + PickedAgeGroup(oldestYear, defaultTeamSize(state.sport, state.seasonEndYear - oldestYear))).sortedByDescending { it.oldestYear },
                problem = null
            )
        }
    }

    fun onTeamSizeChange(oldestYear: Int, teamSize: Int) = updatePicked(oldestYear) { it.copy(teamSize = teamSize) }

    /** Futsal: the age group takes the next younger year too. */
    fun onJoinYoungerChange(oldestYear: Int, join: Boolean) = updatePicked(oldestYear) { it.copy(joinYounger = join) }

    fun onTeamsChange(oldestYear: Int, teams: Int) = updatePicked(oldestYear) { it.copy(teams = teams.coerceIn(1, MAX_WIZARD_TEAMS)) }

    private fun updatePicked(oldestYear: Int, change: (PickedAgeGroup) -> PickedAgeGroup) = _state.update { state ->
        state.copy(picked = state.picked.map { if (it.oldestYear == oldestYear) change(it) else it }, problem = null)
    }

    fun onNext() = _state.update { state ->
        val valid = when (state.step) {
            0 -> state.clubNameValid
            1 -> state.picked.isNotEmpty()
            else -> true
        }
        if (valid) state.copy(step = state.step + 1, showErrors = false, problem = null) else state.copy(showErrors = true)
    }

    fun onBack() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0), showErrors = false, problem = null) }

    fun onCreate() {
        val state = _state.value
        if (state.submitting || state.picked.isEmpty() || !state.clubNameValid) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, problem = null) }
            val request = AddAgeGroupsRequest(
                clubName = state.clubName.trim(),
                ageGroups = state.picked.map {
                    NewAgeGroup(subName(state.seasonEndYear, it.oldestYear), it.startYear, it.oldestYear, it.teamSize, it.teams)
                },
                spaceName = state.spaceName.takeUnless { state.spaceExists },
                sport = state.sport.takeUnless { state.spaceExists }
            )
            try {
                val result: AgeGroupsAdded = addAgeGroupsUseCase(request, state.crest, state.crestPath)
                _events.emit(
                    AddAgeGroupsEvent.Created(result.added.space.id, result.added.ageGroups.map { it.id }, result.added.teams, result.crestSaved)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _state.update {
                    when {
                        e.code == PLAN_LIMIT -> it.copy(submitting = false, problem = WizardProblem.PLAN_LIMIT)
                        e.statusCode == 409 -> it.copy(
                            submitting = false, step = 1, problem = WizardProblem.YEARS_TAKEN,
                            takenYears = e.rows.orEmpty().mapNotNull { row -> state.picked.getOrNull(row)?.oldestYear }.toSet()
                        )
                        else -> it.copy(submitting = false, problem = WizardProblem.FAILED)
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(submitting = false, problem = WizardProblem.FAILED) }
            }
        }
    }

    private companion object {
        const val PLAN_LIMIT = "PLAN_001"
        // The server's limit (LogoImage.MAX_UPLOAD_BYTES): checked here too, so a big image isn't sent for nothing.
        const val MAX_CREST_BYTES = 5 * 1024 * 1024
    }
}
