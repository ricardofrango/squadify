package com.ricardofrango.squadify.presentation.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetTrainingSessionUseCase
import com.ricardofrango.squadify.domain.usecase.TrainingExercisesUseCase
import com.ricardofrango.squadify.model.SaveExerciseRequest
import com.ricardofrango.squadify.model.TrainingExercise
import com.ricardofrango.squadify.model.TrainingSessionDetails
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
import squadify.app.shared.generated.resources.msg_exercise_save_failed

/** The new / edit exercise form (design 39h). Duration is typed as text and must be a positive number. */
data class ExerciseForm(
    val name: String = "",
    val description: String = "",
    val duration: String = "",
    val objectives: String = ""
) {
    val durationMinutes: Int? get() = duration.trim().toIntOrNull()
    val valid: Boolean get() = name.isNotBlank() && (duration.isBlank() || (durationMinutes ?: 0) > 0)

    fun toRequest() = SaveExerciseRequest(
        name = name.trim(),
        description = description.trim().ifEmpty { null },
        durationMinutes = durationMinutes,
        objectives = objectives.trim().ifEmpty { null }
    )
}

internal fun TrainingExercise.toForm() = ExerciseForm(name, description.orEmpty(), durationMinutes?.toString().orEmpty(), objectives.orEmpty())

/** The library filtered by [query] on name and description, leaving out what the session already has. */
internal fun filterLibrary(library: List<TrainingExercise>, query: String, inSession: Set<String>): List<TrainingExercise> {
    val q = query.trim().lowercase()
    return library.filter { it.id !in inSession && (q.isEmpty() || it.name.lowercase().contains(q) || it.description?.lowercase()?.contains(q) == true) }
}

data class ExerciseLibraryUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val session: TrainingSessionDetails? = null,
    val library: List<TrainingExercise> = emptyList(),
    val query: String = "",
    // Picked, in the order picked (they're added in that order).
    val selected: List<String> = emptyList(),
    // The form, when creating (editing == null) or editing an exercise.
    val form: ExerciseForm? = null,
    val editing: TrainingExercise? = null,
    val saving: Boolean = false,
    val error: String? = null
)

/** Add exercises to a session from its age group's library, create new ones, edit them (design 39h). */
class ExerciseLibraryViewModel(
    private val getTrainingSessionUseCase: GetTrainingSessionUseCase,
    private val trainingExercisesUseCase: TrainingExercisesUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ExerciseLibraryUiState())
    val state: StateFlow<ExerciseLibraryUiState> = _state.asStateFlow()

    // The session got new exercises: go back to it.
    private val _done = MutableSharedFlow<Unit>()
    val done: SharedFlow<Unit> = _done.asSharedFlow()

    private var sessionId: String? = null

    fun start(sessionId: String, force: Boolean = false) {
        if (!force && this.sessionId != null) return
        this.sessionId = sessionId
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val session = getTrainingSessionUseCase(sessionId)
                val library = trainingExercisesUseCase.library(session.ageGroup.id)
                _state.update { it.copy(loading = false, session = session, library = library) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun toggle(exerciseId: String) = _state.update {
        it.copy(selected = if (exerciseId in it.selected) it.selected - exerciseId else it.selected + exerciseId)
    }

    fun newExercise() = _state.update { it.copy(form = ExerciseForm(name = it.query.trim()), editing = null, error = null) }
    fun edit(exercise: TrainingExercise) = _state.update { it.copy(form = exercise.toForm(), editing = exercise, error = null) }
    fun closeForm() = _state.update { it.copy(form = null, editing = null, error = null) }
    fun onFormChange(form: ExerciseForm) = _state.update { it.copy(form = form, error = null) }

    /** Adds the picked exercises after the session's own. */
    fun addSelected() {
        val s = _state.value
        if (s.selected.isEmpty()) return
        addToSession(s.selected)
    }

    /** New: saves it to the library and adds it to the session. Edit: saves it everywhere it's used. */
    fun saveForm() {
        val s = _state.value
        val form = s.form ?: return
        val ageGroupId = s.session?.ageGroup?.id ?: return
        if (!form.valid || s.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val editing = s.editing
                if (editing != null) {
                    val updated = trainingExercisesUseCase.update(editing.id, form.toRequest())
                    _state.update { st -> st.copy(saving = false, form = null, editing = null, library = st.library.map { if (it.id == updated.id) updated else it }) }
                } else {
                    val created = trainingExercisesUseCase.create(ageGroupId, form.toRequest())
                    _state.update { it.copy(library = it.library + created) }
                    addToSession(listOf(created.id))
                }
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_exercise_save_failed)
                _state.update { it.copy(saving = false, error = message) }
            }
        }
    }

    private fun addToSession(ids: List<String>) {
        val sessionId = sessionId ?: return
        val current = _state.value.session?.exercises?.map { it.id }.orEmpty()
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                trainingExercisesUseCase.setForSession(sessionId, current + ids.filter { it !in current })
                _done.emit(Unit)
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_exercise_save_failed)
                _state.update { it.copy(saving = false, error = message) }
            }
        }
    }
}
