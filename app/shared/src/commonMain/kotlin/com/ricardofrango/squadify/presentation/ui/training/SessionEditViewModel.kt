package com.ricardofrango.squadify.presentation.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetTrainingSessionsUseCase
import com.ricardofrango.squadify.domain.usecase.SaveTrainingSessionUseCase
import com.ricardofrango.squadify.model.SaveTrainingSessionRequest
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.model.TrainingSessions
import com.ricardofrango.squadify.presentation.errorMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_session_save_failed
import kotlin.time.Clock

/** Add / edit one session (design 39e). */
data class SessionForm(
    val date: LocalDate? = null,
    val start: LocalTime = LocalTime(18, 0),
    val end: LocalTime = LocalTime(19, 0),
    val location: String = "",
    val notes: String = ""
)

internal fun TrainingSession.toForm() = SessionForm(date, startTime, endTime, location.orEmpty(), notes.orEmpty())

/** Another session already at the form's date and start time (it would be replaced). */
internal fun SessionForm.clash(sessions: List<TrainingSession>, editingId: String?): TrainingSession? =
    sessions.firstOrNull { it.id != editingId && it.date == date && it.startTime == start }

data class SessionEditUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val sessions: TrainingSessions? = null,
    val editingId: String? = null,
    val form: SessionForm = SessionForm(),
    val original: SessionForm? = null,
    val saving: Boolean = false,
    val error: String? = null
) {
    val valid: Boolean
        get() {
            val date = form.date ?: return false
            if (sessions == null) return false
            return form.end > form.start
        }
    // Edit: Save waits for a change.
    val canSave: Boolean get() = valid && !saving && form != original
}

class SessionEditViewModel(
    private val getTrainingSessionsUseCase: GetTrainingSessionsUseCase,
    private val saveTrainingSessionUseCase: SaveTrainingSessionUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SessionEditUiState())
    val state: StateFlow<SessionEditUiState> = _state.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    private var ageGroupId: String? = null

    /** [sessionId] null adds one, on [date] (or today; any day, not bound by the season), at the club's stadium. */
    fun start(ageGroupId: String, sessionId: String?, date: LocalDate?, force: Boolean = false) {
        if (!force && this.ageGroupId != null) return
        this.ageGroupId = ageGroupId
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val sessions = getTrainingSessionsUseCase(ageGroupId)
                val editing = sessionId?.let { id -> sessions.sessions.firstOrNull { it.id == id } }
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                val form = editing?.toForm() ?: SessionForm(
                    date = date ?: today,
                    location = sessions.stadiumLocation.orEmpty()
                )
                _state.update { SessionEditUiState(loading = false, sessions = sessions, editingId = editing?.id, form = form, original = if (editing != null) form else null) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    fun onFormChange(form: SessionForm) = _state.update { it.copy(form = form, error = null) }

    fun save() {
        val s = _state.value
        val ageGroupId = ageGroupId ?: return
        val date = s.form.date ?: return
        if (!s.canSave) return
        val request = SaveTrainingSessionRequest(date, s.form.start, s.form.end, s.form.location.trim().ifEmpty { null }, s.form.notes.trim().ifEmpty { null })
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                if (s.editingId != null) saveTrainingSessionUseCase.update(s.editingId, request) else saveTrainingSessionUseCase.add(ageGroupId, request)
                _saved.emit(Unit)
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_session_save_failed)
                _state.update { it.copy(saving = false, error = message) }
            }
        }
    }
}
