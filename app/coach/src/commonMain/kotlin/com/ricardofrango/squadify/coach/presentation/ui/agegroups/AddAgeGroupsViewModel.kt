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
import com.ricardofrango.squadify.model.birthYearsOverlap
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
    // One box per age group, starting with an empty one.
    val rows: List<AgeGroupRow> = listOf(AgeGroupRow(key = 0)),
    val showErrors: Boolean = false,
    val submitting: Boolean = false,
    val problem: WizardProblem? = null,
    // The boxes the server said the club already has (their keys).
    val takenRows: Set<Int> = emptySet()
) {
    val usedClubs: List<SpaceClub>
        get() = existing.mapNotNull { ageGroup -> clubNameOf(ageGroup)?.let { it to ageGroup.logoUrl } }
            .groupBy({ it.first.lowercase() }, { it })
            .map { (_, named) -> SpaceClub(named.first().first, named.firstNotNullOfOrNull { it.second }) }
            .sortedBy { it.name.lowercase() }

    val clubNameValid: Boolean get() = clubName.isNotBlank() && AGE_GROUP_CLUB_SEPARATOR !in clubName.trim()

    /** Whether another box can be added: the plan's room this season, when it has a limit. */
    val canAddRow: Boolean get() = ageGroupsLeft == null || rows.size < ageGroupsLeft

    val teamCount: Int get() = rows.sumOf { it.teams }

    /** What's wrong with [row], or null: a year of Sub 5 to Sub 19, in no other box, the club's not already; a size. */
    fun problemOf(row: AgeGroupRow): RowProblem? {
        val oldest = row.oldestYear
        val start = row.startYear
        return when {
            oldest == null || start == null -> RowProblem.YEAR_MISSING
            oldest !in wizardYearRange(seasonEndYear) -> RowProblem.YEAR_OUT_OF_RANGE
            rows.takeWhile { it.key != row.key }.any { other ->
                val otherOldest = other.oldestYear ?: return@any false
                birthYearsOverlap(start, oldest, other.startYear ?: otherOldest, otherOldest)
            } -> RowProblem.YEAR_REPEATED
            row.key in takenRows || clubHasYears(existing, seasonId, clubName, start, oldest) -> RowProblem.YEAR_TAKEN
            row.teamSize == null -> RowProblem.SIZE_MISSING
            else -> null
        }
    }

    val rowsValid: Boolean get() = rows.isNotEmpty() && rows.all { problemOf(it) == null }
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

    fun onClubNameChange(name: String) = _state.update { it.copy(clubName = name, problem = null, takenRows = emptySet()) }

    fun onCrestPicked(bytes: ByteArray) = _state.update {
        if (bytes.size > MAX_CREST_BYTES) it.copy(problem = WizardProblem.CREST_TOO_BIG)
        else it.copy(crest = bytes, crestPath = null, problem = null)
    }

    /** A club the space already uses: its name and crest. */
    fun onUsedClubPicked(club: SpaceClub) = _state.update { it.copy(clubName = club.name, crest = null, crestPath = club.logoUrl, problem = null, takenRows = emptySet()) }

    /** A new space's sport: the sizes picked so far are the other sport's, so they're picked again. */
    fun onSportChange(sport: SportType) = _state.update { state ->
        if (state.spaceExists || sport == state.sport) state
        else state.copy(sport = sport, rows = state.rows.map { it.copy(teamSize = null, joinYounger = false) })
    }

    /** Another box, empty, while the plan has room. */
    fun onAddRow() = _state.update { state ->
        if (!state.canAddRow) state
        else state.copy(rows = state.rows + AgeGroupRow(key = (state.rows.maxOfOrNull { it.key } ?: -1) + 1), showErrors = false, problem = null)
    }

    fun onRemoveRow(key: Int) = _state.update { state ->
        if (state.rows.size <= 1) state else state.copy(rows = state.rows.filterNot { it.key == key }, takenRows = state.takenRows - key, problem = null)
    }

    /** The birth year as typed: digits only, four at most. */
    fun onYearChange(key: Int, text: String) = updateRow(key) { it.copy(yearText = text.filter(Char::isDigit).take(4)) }

    fun onTeamSizeChange(key: Int, teamSize: Int) = updateRow(key) { it.copy(teamSize = teamSize) }

    /** Futsal: the age group takes the next younger year too. */
    fun onJoinYoungerChange(key: Int, join: Boolean) = updateRow(key) { it.copy(joinYounger = join) }

    fun onTeamsChange(key: Int, teams: Int) = updateRow(key) { it.copy(teams = teams.coerceIn(1, MAX_WIZARD_TEAMS)) }

    private fun updateRow(key: Int, change: (AgeGroupRow) -> AgeGroupRow) = _state.update { state ->
        state.copy(rows = state.rows.map { if (it.key == key) change(it) else it }, takenRows = state.takenRows - key, problem = null)
    }

    fun onNext() = _state.update { state ->
        val valid = when (state.step) {
            0 -> state.clubNameValid
            1 -> state.rowsValid
            else -> true
        }
        if (valid) state.copy(step = state.step + 1, showErrors = false, problem = null) else state.copy(showErrors = true)
    }

    fun onBack() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0), showErrors = false, problem = null) }

    fun onCreate() {
        val state = _state.value
        if (state.submitting || !state.rowsValid || !state.clubNameValid) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, problem = null) }
            val request = AddAgeGroupsRequest(
                clubName = state.clubName.trim(),
                ageGroups = state.rows.map {
                    val oldest = it.oldestYear!!
                    NewAgeGroup(subName(state.seasonEndYear, oldest), it.startYear!!, oldest, it.teamSize, it.teams)
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
                            submitting = false, step = 1, problem = WizardProblem.YEARS_TAKEN, showErrors = true,
                            takenRows = e.rows.orEmpty().mapNotNull { row -> state.rows.getOrNull(row)?.key }.toSet()
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
