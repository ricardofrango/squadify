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

// Rate players: a text box per player for a rating from 0 to 10 with up to one decimal ("7.5" or "7,5").
// 0 means absent; an empty box means not rated. Saving is partial.

/** A box's text read as a rating: [Valid] (null when empty) or [Invalid]. */
internal sealed interface RatingInput {
    data class Valid(val rating: Double?) : RatingInput
    data object Invalid : RatingInput
}

private val RatingPattern = Regex("""^\d{1,2}([.,]\d?)?$""")

internal fun parseRating(text: String): RatingInput {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return RatingInput.Valid(null)
    if (!RatingPattern.matches(trimmed)) return RatingInput.Invalid
    // "7." (still typing) reads as 7.
    val value = trimmed.replace(',', '.').trimEnd('.').toDouble()
    return if (value <= 10.0) RatingInput.Valid(value) else RatingInput.Invalid
}

/** How a stored rating shows in its box: "8", "7.5", or empty when not rated. */
internal fun ratingText(rating: Double?): String = when {
    rating == null -> ""
    rating % 1.0 == 0.0 -> rating.toInt().toString()
    else -> rating.oneDecimalString()
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
    // What's typed in each player's box.
    val texts: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val error: String? = null
) {
    private val inputs: Map<String, RatingInput> get() = texts.mapValues { parseRating(it.value) }
    /** The players whose box isn't a valid rating; Save waits for them. */
    val invalid: Set<String> get() = inputs.filterValues { it is RatingInput.Invalid }.keys
    /** The valid ratings typed (players with an invalid box keep what was loaded). */
    val current: Map<String, Double?> get() = original + inputs.mapNotNull { (id, input) -> (input as? RatingInput.Valid)?.let { id to it.rating } }
    val dirty: Boolean get() = ratingChanges(original, current).isNotEmpty()
    val canSave: Boolean get() = dirty && invalid.isEmpty() && !saving
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
            RatePlayersUiState(loading = false, ratings = ratings, original = values, texts = values.mapValues { (_, v) -> ratingText(v) })
        }
    }

    fun onText(playerId: String, text: String) = _state.update { it.copy(texts = it.texts + (playerId to text), error = null) }

    fun save() {
        val id = sessionId ?: return
        val s = _state.value
        val changes = ratingChanges(s.original, s.current)
        if (!s.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                trainingRatingsUseCase.save(id, changes)
                _state.update { it.copy(saving = false, original = it.current, texts = it.current.mapValues { (_, v) -> ratingText(v) }) }
                _saved.emit(Unit)
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_ratings_save_failed)
                _state.update { it.copy(saving = false, error = message) }
            }
        }
    }
}
