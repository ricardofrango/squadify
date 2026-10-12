package com.ricardofrango.squadify.presentation.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.TrainingRatingsUseCase
import com.ricardofrango.squadify.model.TrainingRatingChange
import com.ricardofrango.squadify.model.TrainingRatings
import com.ricardofrango.squadify.presentation.errorMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_ratings_save_failed
import kotlin.math.round

// Rate players (design 39g): tap a player, then a quick 5–9 (whole number, and the next unrated player
// opens), − / + by 0.1 (0.1–10.0, stays on the player), or Absent (0, next). Saving is partial.

/** [value] stepped by [delta] tenths, kept in 0.1–10.0 (stepping never makes a player Absent). */
internal fun stepRating(value: Double?, delta: Int): Double {
    val tenths = round((value ?: 6.0) * 10).toInt() + delta
    return tenths.coerceIn(1, 100) / 10.0
}

/** The next player after [current] (wrapping) who has no rating yet, or null when everyone is rated. */
internal fun nextUnrated(playerIds: List<String>, ratings: Map<String, Double?>, current: String?): String? {
    if (playerIds.isEmpty()) return null
    val start = current?.let { playerIds.indexOf(it) } ?: -1
    return (1..playerIds.size).map { playerIds[(start + it) % playerIds.size] }.firstOrNull { ratings[it] == null }
}

/** What changed against what was loaded: a new value, or null when a rating was cleared. */
internal fun ratingChanges(original: Map<String, Double?>, current: Map<String, Double?>): List<TrainingRatingChange> =
    (original.keys + current.keys).distinct().mapNotNull { id ->
        val before = original[id]
        val after = current[id]
        if (before == after) null else TrainingRatingChange(id, after)
    }

data class RatePlayersUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val ratings: TrainingRatings? = null,
    val original: Map<String, Double?> = emptyMap(),
    val current: Map<String, Double?> = emptyMap(),
    // The open player row.
    val open: String? = null,
    val saving: Boolean = false,
    val error: String? = null
) {
    val dirty: Boolean get() = ratingChanges(original, current).isNotEmpty()
    val ratedCount: Int get() = current.values.count { it != null }
    val absentCount: Int get() = current.values.count { it == 0.0 }
}

class RatePlayersViewModel(
    private val trainingRatingsUseCase: TrainingRatingsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(RatePlayersUiState())
    val state: StateFlow<RatePlayersUiState> = _state.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    private var sessionId: String? = null

    fun start(sessionId: String, force: Boolean = false) {
        if (!force && this.sessionId != null) return
        this.sessionId = sessionId
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                apply(trainingRatingsUseCase.get(sessionId))
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    private fun apply(ratings: TrainingRatings) {
        val byPlayer = ratings.ratings.associate { it.playerId to it.rating }
        val values: Map<String, Double?> = ratings.players.associate { it.id to byPlayer[it.id] }
        _state.update {
            RatePlayersUiState(
                loading = false, ratings = ratings, original = values, current = values,
                open = nextUnrated(ratings.players.map { p -> p.id }, values, null)
            )
        }
    }

    fun open(playerId: String) = _state.update { it.copy(open = if (it.open == playerId) null else playerId) }

    /** A quick number or Absent: sets it and opens the next unrated player. */
    fun setAndNext(playerId: String, rating: Double) = _state.update { s ->
        val current = s.current + (playerId to rating)
        s.copy(current = current, open = nextUnrated(s.ratings?.players?.map { it.id }.orEmpty(), current, playerId), error = null)
    }

    /** − / +: adjusts by 0.1 and stays on the player. */
    fun step(playerId: String, delta: Int) = _state.update { s ->
        s.copy(current = s.current + (playerId to stepRating(s.current[playerId], delta)), error = null)
    }

    fun save() {
        val id = sessionId ?: return
        val s = _state.value
        val changes = ratingChanges(s.original, s.current)
        if (changes.isEmpty() || s.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                trainingRatingsUseCase.save(id, changes)
                _state.update { it.copy(saving = false, original = it.current) }
                _saved.emit(Unit)
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_ratings_save_failed)
                _state.update { it.copy(saving = false, error = message) }
            }
        }
    }
}
