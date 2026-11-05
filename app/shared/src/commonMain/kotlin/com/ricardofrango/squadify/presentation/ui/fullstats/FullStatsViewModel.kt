package com.ricardofrango.squadify.presentation.ui.fullstats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.data.remote.isNotInPlan
import com.ricardofrango.squadify.domain.repository.UiPreferencesRepository
import com.ricardofrango.squadify.domain.usecase.GetFullStatsUseCase
import com.ricardofrango.squadify.model.FullStatsColumn
import com.ricardofrango.squadify.model.FullStatsData
import com.ricardofrango.squadify.model.FullStatsQuery
import com.ricardofrango.squadify.model.PlayerPosition
import com.ricardofrango.squadify.model.StatsFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** The period presets of the date filter; [CUSTOM] uses the picked from / to. */
enum class StatsPeriod { THIS_WEEK, LAST_7_DAYS, THIS_MONTH, SEASON, CUSTOM }

/**
 * The inclusive date range a preset covers on [today]: the week from Monday, the last seven days with
 * today, the month from the 1st. [StatsPeriod.SEASON] is open, so every game and training of the season counts.
 */
fun StatsPeriod.range(today: LocalDate, customFrom: LocalDate?, customTo: LocalDate?): Pair<LocalDate?, LocalDate?> = when (this) {
    StatsPeriod.THIS_WEEK -> today.minus(DatePeriod(days = today.dayOfWeek.isoDayNumber - 1)) to today
    StatsPeriod.LAST_7_DAYS -> today.minus(DatePeriod(days = 6)) to today
    StatsPeriod.THIS_MONTH -> LocalDate(today.year, today.month, 1) to today
    StatsPeriod.SEASON -> null to null
    StatsPeriod.CUSTOM -> customFrom to customTo
}

/** Hidden columns, stored as their names ("GOALS,RED_CARDS"); unknown names are ignored. */
internal fun parseHiddenColumns(stored: String?): Set<FullStatsColumn> =
    stored.orEmpty().split(',').mapNotNull { name -> FullStatsColumn.entries.firstOrNull { it.name == name } }.toSet()

internal fun Set<FullStatsColumn>.toStored(): String = sortedBy { it.ordinal }.joinToString(",") { it.name }

sealed class FullStatsUiState {
    data object Loading : FullStatsUiState()
    data object Error : FullStatsUiState()
    // Not on the space's plan (the coach app's Free plan).
    data object NotInPlan : FullStatsUiState()

    data class Loaded(
        val data: FullStatsData,
        val period: StatsPeriod,
        val customFrom: LocalDate?,
        val customTo: LocalDate?,
        val type: StatsFilter,
        // The team filter; null = the whole age group. Entering from a team preselects it.
        val teamId: String?,
        // The position filter; null = every player.
        val position: PlayerPosition?,
        // null = by the player's name.
        val sort: FullStatsColumn?,
        val descending: Boolean,
        val hidden: Set<FullStatsColumn>,
        val today: LocalDate
    ) : FullStatsUiState() {
        val query: FullStatsQuery
            get() = period.range(today, customFrom, customTo).let { (from, to) -> FullStatsQuery(from, to, type, teamId, position) }

        /** The stat columns shown: not hidden, and the rankings only for staff. */
        val columns: List<FullStatsColumn>
            get() = FullStatsColumn.entries.filter { it !in hidden && (!it.rating || data.canSeeRatings) }
    }
}

/**
 * The Full stats screen: one table of every player's stats for a team or an age group. The data is
 * loaded once; the period, match type, team, position, sort and columns are all applied client-side
 * ([com.ricardofrango.squadify.model.table]). The hidden columns are remembered on the device.
 */
class FullStatsViewModel(
    private val getFullStatsUseCase: GetFullStatsUseCase,
    private val uiPreferencesRepository: UiPreferencesRepository
) : ViewModel() {

    private val _state = MutableStateFlow<FullStatsUiState>(FullStatsUiState.Loading)
    val state: StateFlow<FullStatsUiState> = _state.asStateFlow()

    /**
     * Exactly one of [teamId] / [ageGroupId]. Either way the whole age group is loaded, so the team
     * filter is the same; from a team, that team starts selected (and can be changed).
     */
    fun load(teamId: String?, ageGroupId: String?, force: Boolean = false) {
        if (!force && _state.value is FullStatsUiState.Loaded) return
        viewModelScope.launch {
            val previous = _state.value as? FullStatsUiState.Loaded
            if (previous == null) _state.update { FullStatsUiState.Loading }
            _state.update {
                try {
                    val data = if (teamId != null) getFullStatsUseCase.team(teamId) else getFullStatsUseCase.ageGroup(ageGroupId!!)
                    previous?.copy(data = data) ?: FullStatsUiState.Loaded(
                        data = data,
                        period = StatsPeriod.SEASON,
                        customFrom = null,
                        customTo = null,
                        type = StatsFilter.ALL,
                        teamId = teamId,
                        position = null,
                        // Staff open on the final ranking, the screen's point; everyone else on goals.
                        sort = if (data.canSeeRatings) FullStatsColumn.FINAL_RATING else FullStatsColumn.GOALS,
                        descending = true,
                        hidden = parseHiddenColumns(uiPreferencesRepository.get(HIDDEN_COLUMNS_KEY)),
                        today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                    )
                } catch (e: Exception) {
                    if (e.isNotInPlan) FullStatsUiState.NotInPlan else previous ?: FullStatsUiState.Error
                }
            }
        }
    }

    fun onPeriodSelected(period: StatsPeriod) = updateLoaded { it.copy(period = period) }

    fun onCustomRange(from: LocalDate, to: LocalDate) = updateLoaded { it.copy(period = StatsPeriod.CUSTOM, customFrom = from, customTo = to) }

    fun onTypeSelected(type: StatsFilter) = updateLoaded { it.copy(type = type) }

    fun onTeamSelected(teamId: String?) = updateLoaded { it.copy(teamId = teamId) }

    fun onPositionSelected(position: PlayerPosition?) = updateLoaded { it.copy(position = position) }

    /**
     * The same column again flips the direction; a new stat column starts highest first, the player
     * column ([column] null) A to Z and the position goalkeeper first.
     */
    fun onSort(column: FullStatsColumn?) = updateLoaded {
        if (it.sort == column) it.copy(descending = !it.descending)
        else it.copy(sort = column, descending = column != null && column != FullStatsColumn.POSITION)
    }

    fun onToggleColumn(column: FullStatsColumn) = updateLoaded {
        val hidden = if (column in it.hidden) it.hidden - column else it.hidden + column
        uiPreferencesRepository.set(HIDDEN_COLUMNS_KEY, hidden.toStored())
        it.copy(hidden = hidden)
    }

    private fun updateLoaded(block: (FullStatsUiState.Loaded) -> FullStatsUiState.Loaded) =
        _state.update { if (it is FullStatsUiState.Loaded) block(it) else it }

    private companion object {
        const val HIDDEN_COLUMNS_KEY = "full_stats_hidden_columns"
    }
}
