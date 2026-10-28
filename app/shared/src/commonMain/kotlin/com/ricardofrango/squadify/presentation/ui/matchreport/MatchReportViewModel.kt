package com.ricardofrango.squadify.presentation.ui.matchreport

import com.ricardofrango.squadify.presentation.ui.matchdetails.CallUpSort
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.domain.usecase.GetMatchReportUseCase
import com.ricardofrango.squadify.domain.usecase.SaveMatchReportUseCase
import com.ricardofrango.squadify.presentation.errorMessage
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_report_load_failed_title
import squadify.app.shared.generated.resources.msg_report_save_failed

class MatchReportViewModel(
    private val getMatchReportUseCase: GetMatchReportUseCase,
    private val saveMatchReportUseCase: SaveMatchReportUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<MatchReportUiState>(MatchReportUiState.Loading)
    val state: StateFlow<MatchReportUiState> = _state.asStateFlow()

    private var matchId: String = ""

    // Flips to true on the first successful save of this visit, so the screen can tell Match Details /
    // Home to reload straight away -- not only on the way out, since system back can leave without it.
    private val _savedSomething = MutableStateFlow(false)
    val savedSomething: StateFlow<Boolean> = _savedSomething.asStateFlow()

    fun load(matchId: String, startEditing: Boolean, force: Boolean = false) {
        this.matchId = matchId
        if (!force && _state.value is MatchReportUiState.Idle) return
        viewModelScope.launch {
            _state.update { MatchReportUiState.Loading }
            try {
                val details = getMatchReportUseCase(matchId)
                val ratings = details.playerStats.associate { it.player.id to it.stats.rating }
                val editing = startEditing && details.canEdit
                val anyUnrated = details.playerStats.any { it.stats.rating == null }
                _state.update {
                    MatchReportUiState.Idle(
                        details = details,
                        editing = editing,
                        originalFields = ReportFields.from(details.report, details.match.startingFormation),
                        fields = ReportFields.from(details.report, details.match.startingFormation),
                        originalRatings = ratings,
                        ratings = ratings,
                        // Design 24e: open on Players while anyone is unrated, otherwise Team.
                        tab = if (details.playerStats.isNotEmpty() && anyUnrated) ReportTab.PLAYERS else ReportTab.TEAM
                    )
                }
            } catch (e: Exception) {
                if (e is ApiException && e.statusCode == HttpStatusCode.Forbidden.value) {
                    _state.update { MatchReportUiState.NoPermission }
                } else {
                    _state.update { MatchReportUiState.Error(errorMessage(e, Res.string.msg_report_load_failed_title)) }
                }
            }
        }
    }

    fun onStartEditing() = updateIdle { if (it.details.canEdit) it.copy(editing = true, openRatingFor = null) else it }

    fun onTabSelected(tab: ReportTab) = updateIdle { it.copy(tab = tab, openRatingFor = null) }

    fun onFieldsChanged(transform: (ReportFields) -> ReportFields) = updateIdle { it.copy(fields = transform(it.fields)) }

    fun onReadSortSelected(sort: ReportPlayerSort) = updateIdle { it.copy(readSort = sort) }

    fun onRatingSortSelected(sort: CallUpSort) = updateIdle { it.copy(ratingSort = sort) }

    /** Tapping a player's rating button opens their grid, or closes it if it's already open. */
    fun onToggleRating(playerId: String) =
        updateIdle { it.copy(openRatingFor = if (it.openRatingFor == playerId) null else playerId) }

    /** Picking a value saves it locally and opens the next unrated player (wrapping), or closes. */
    fun onRate(playerId: String, rating: Int) = updateIdle { state ->
        val ratings = state.ratings + (playerId to rating)
        state.copy(
            ratings = ratings,
            openRatingFor = nextUnrated(state.players.map { it.player.id }, ratings, playerId)
        )
    }

    /** "Clear" empties the rating and doesn't advance. */
    fun onClearRating(playerId: String) = updateIdle { it.copy(ratings = it.ratings + (playerId to null)) }

    /** "Rate next": jump to the first unrated player. */
    fun onRateNext() = updateIdle { state ->
        state.copy(tab = ReportTab.PLAYERS, openRatingFor = nextUnrated(state.players.map { it.player.id }, state.ratings, null))
    }

    fun onToastShown() = updateIdle { it.copy(toast = null, saveErrorMessage = null) }

    fun onSave(action: SaveAction) {
        val current = _state.value as? MatchReportUiState.Idle ?: return
        if (current.saving != null) return
        val wasCompleted = current.isCompleted
        _state.update { current.copy(saving = action, openRatingFor = null) }
        viewModelScope.launch {
            try {
                val saved = saveMatchReportUseCase(matchId, current.toRequest(complete = action == SaveAction.COMPLETE))
                _savedSomething.value = true
                updateIdle { state ->
                    state.copy(
                        details = state.details.copy(
                            report = saved,
                            playerStats = state.details.playerStats.map { row ->
                                row.copy(stats = row.stats.copy(rating = state.ratings[row.player.id]))
                            }
                        ),
                        // The server trims text and turns blanks into nulls -- adopt its version.
                        originalFields = ReportFields.from(saved, state.details.match.startingFormation),
                        fields = ReportFields.from(saved, state.details.match.startingFormation),
                        originalRatings = state.ratings,
                        saving = null,
                        failedAction = null,
                        toast = when {
                            action == SaveAction.DRAFT -> ReportToast.DRAFT_SAVED
                            wasCompleted -> ReportToast.REPORT_UPDATED
                            else -> ReportToast.REPORT_COMPLETED
                        }
                    )
                }
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_report_save_failed)
                updateIdle { it.copy(saving = null, failedAction = action, saveErrorMessage = message) }
            }
        }
    }

    private fun updateIdle(transform: (MatchReportUiState.Idle) -> MatchReportUiState.Idle) {
        _state.update { (it as? MatchReportUiState.Idle)?.let(transform) ?: it }
    }
}
