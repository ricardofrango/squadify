package com.ricardofrango.squadify.presentation.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.CreateTrainingSessionsUseCase
import com.ricardofrango.squadify.domain.usecase.GetTrainingSessionsUseCase
import com.ricardofrango.squadify.model.CreateTrainingSessionsRequest
import com.ricardofrango.squadify.model.MAX_BULK_SESSIONS
import com.ricardofrango.squadify.model.TrainingSessions
import com.ricardofrango.squadify.model.TrainingSessionsPreview
import com.ricardofrango.squadify.model.TrainingSlot
import com.ricardofrango.squadify.model.plannedSessions
import com.ricardofrango.squadify.model.publicHolidaysBetween
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
import squadify.app.shared.generated.resources.msg_create_sessions_failed
import squadify.app.shared.generated.resources.msg_preview_failed
import kotlin.time.Clock

/** One weekly slot as edited: weekdays (ISO numbers) sharing a start and an end. */
data class SlotForm(
    val weekdays: Set<Int> = emptySet(),
    val start: LocalTime = LocalTime(18, 0),
    val end: LocalTime = LocalTime(19, 0)
) {
    val valid: Boolean get() = weekdays.isNotEmpty() && end > start
}

/** Create sessions (design 39c): nothing here is kept for next time. */
data class CreateSessionsForm(
    val slots: List<SlotForm> = listOf(SlotForm()),
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val location: String = "",
    val includeHolidays: Boolean = false
)

internal fun CreateSessionsForm.toRequest(confirmReplacingContent: Boolean = false): CreateTrainingSessionsRequest? {
    val start = startDate ?: return null
    val end = endDate ?: return null
    return CreateTrainingSessionsRequest(
        slots = slots.map { TrainingSlot(it.weekdays.sorted(), it.start, it.end) },
        startDate = start,
        endDate = end,
        location = location.trim().ifEmpty { null },
        includeHolidays = includeHolidays,
        confirmReplacingContent = confirmReplacingContent
    )
}

/** How many sessions the form makes, worked out on the device for the Preview button; 0 when it can't. */
internal fun CreateSessionsForm.sessionCount(sessions: TrainingSessions): Int {
    val start = startDate ?: return 0
    val end = endDate ?: return 0
    if (slots.isEmpty() || !slots.all { it.valid } || end < start) return 0
    val holidays = publicHolidaysBetween(sessions.country, start, end)
    return plannedSessions(slots.map { TrainingSlot(it.weekdays.toList(), it.start, it.end) }, start, end, holidays, includeHolidays)
        .count { it.skippedHoliday == null }
}

data class CreateSessionsUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val sessions: TrainingSessions? = null,
    val form: CreateSessionsForm = CreateSessionsForm(),
    val preview: TrainingSessionsPreview? = null,
    val previewing: Boolean = false,
    val creating: Boolean = false,
    // Replacing sessions with exercises or ratings asks first (design 39d).
    val askConfirm: Boolean = false,
    val error: String? = null
) {
    val count: Int get() = sessions?.let { form.sessionCount(it) } ?: 0
    val canPreview: Boolean get() = count in 1..MAX_BULK_SESSIONS && !previewing
}

class CreateSessionsViewModel(
    private val getTrainingSessionsUseCase: GetTrainingSessionsUseCase,
    private val createTrainingSessionsUseCase: CreateTrainingSessionsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(CreateSessionsUiState())
    val state: StateFlow<CreateSessionsUiState> = _state.asStateFlow()

    private val _created = MutableSharedFlow<Int>()
    val created: SharedFlow<Int> = _created.asSharedFlow()

    private var ageGroupId: String? = null

    /**
     * Loads the season and the stadium: the period opens on today to the season's end. That end is only a
     * starting point; the period can go past it (clubs often train into July), or before the season starts.
     */
    fun start(ageGroupId: String, force: Boolean = false) {
        if (!force && this.ageGroupId != null) return
        this.ageGroupId = ageGroupId
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val sessions = getTrainingSessionsUseCase(ageGroupId)
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                _state.update {
                    it.copy(
                        loading = false,
                        sessions = sessions,
                        form = CreateSessionsForm(startDate = today, endDate = maxOf(today, sessions.season.endDate), location = sessions.stadiumLocation.orEmpty())
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    fun onFormChange(form: CreateSessionsForm) = _state.update { it.copy(form = form, error = null) }

    fun preview() {
        val ageGroupId = ageGroupId ?: return
        val request = _state.value.form.toRequest() ?: return
        if (!_state.value.canPreview) return
        viewModelScope.launch {
            _state.update { it.copy(previewing = true, error = null) }
            try {
                val preview = createTrainingSessionsUseCase.preview(ageGroupId, request)
                _state.update { it.copy(previewing = false, preview = preview) }
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_preview_failed)
                _state.update { it.copy(previewing = false, error = message) }
            }
        }
    }

    /** "Edit": back to the form with everything kept. */
    fun backToForm() = _state.update { it.copy(preview = null, askConfirm = false, error = null) }

    fun create() {
        val preview = _state.value.preview ?: return
        if (preview.replacingWithContent > 0) _state.update { it.copy(askConfirm = true) } else doCreate(confirm = false)
    }

    fun dismissConfirm() = _state.update { it.copy(askConfirm = false) }

    fun confirmCreate() {
        _state.update { it.copy(askConfirm = false) }
        doCreate(confirm = true)
    }

    private fun doCreate(confirm: Boolean) {
        val ageGroupId = ageGroupId ?: return
        val request = _state.value.form.toRequest(confirmReplacingContent = confirm) ?: return
        viewModelScope.launch {
            _state.update { it.copy(creating = true, error = null) }
            try {
                val count = createTrainingSessionsUseCase.create(ageGroupId, request)
                _created.emit(count)
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_create_sessions_failed)
                _state.update { it.copy(creating = false, error = message) }
            }
        }
    }
}
