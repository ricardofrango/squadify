package com.ricardofrango.squadify.presentation.ui.opponents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.domain.usecase.CreateOpponentUseCase
import com.ricardofrango.squadify.domain.usecase.GetOpponentDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetOpponentListUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateOpponentUseCase
import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.OPPONENT_SHORT_NAME_MAX
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.UpdateOpponentRequest
import com.ricardofrango.squadify.presentation.errorMessage
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_create_opponent_failed
import squadify.app.shared.generated.resources.msg_opponent_save_failed

/** What the opponent form holds, as typed. */
data class OpponentForm(
    val name: String = "",
    val shortName: String = "",
    val stadiumName: String = "",
    val stadiumAddress: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val notes: String = ""
)

data class OpponentFormUiState(
    val clubName: String? = null,
    val form: OpponentForm = OpponentForm(),
    // Edit only: the opponent as loaded, so Save waits for a change. Null when creating.
    val original: OpponentForm? = null,
    // Edit only: loading the opponent, or that failed.
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    // "An opponent with this name already exists": checked when Name loses focus and on submit.
    val duplicateName: Boolean = false,
    val submitting: Boolean = false,
    // Localized copy only, never a raw exception message (AI_GUIDELINES.md).
    val error: String? = null
) {
    val canSubmit: Boolean
        get() = form.name.isNotBlank() && !duplicateName && !submitting && !loading && form != original
}

/** Whether [name] is already one of [opponents]' names, ignoring case and surrounding spaces. */
internal fun isDuplicateOpponentName(name: String, opponents: List<Opponent>, exceptId: String? = null): Boolean {
    val wanted = name.trim()
    return wanted.isNotEmpty() && opponents.any { it.id != exceptId && it.name.trim().equals(wanted, ignoreCase = true) }
}

/** The short name as it's typed: uppercase and at most [OPPONENT_SHORT_NAME_MAX] characters. */
internal fun typedShortName(value: String): String = value.uppercase().take(OPPONENT_SHORT_NAME_MAX)

/** What the form starts with when editing [opponent]. */
internal fun Opponent.toForm() = OpponentForm(
    name = name,
    shortName = shortName.orEmpty(),
    stadiumName = stadiumName.orEmpty(),
    stadiumAddress = stadiumAddress.orEmpty(),
    contactEmail = contactEmail.orEmpty(),
    contactPhone = contactPhone.orEmpty(),
    notes = notes.orEmpty()
)

/** The form as an edit: every field is sent, and an emptied one is cleared. */
internal fun OpponentForm.toUpdateRequest() = UpdateOpponentRequest(
    name = name.trim(),
    shortName = shortName.trim(),
    stadiumName = stadiumName.trim(),
    stadiumAddress = stadiumAddress.trim(),
    contactEmail = contactEmail.trim(),
    contactPhone = contactPhone.trim(),
    notes = notes.trim()
)

/** The form as a create request; empty optional fields are left out. */
internal fun OpponentForm.toCreateRequest(ageGroupId: String?) = CreateOpponentRequest(
    name = name.trim(),
    shortName = shortName.trim().ifEmpty { null },
    stadiumName = stadiumName.trim().ifEmpty { null },
    stadiumAddress = stadiumAddress.trim().ifEmpty { null },
    contactEmail = contactEmail.trim().ifEmpty { null },
    contactPhone = contactPhone.trim().ifEmpty { null },
    notes = notes.trim().ifEmpty { null },
    ageGroupId = ageGroupId
)

/** Create opponent and Edit opponent (design 38e). */
class OpponentFormViewModel(
    private val getOpponentListUseCase: GetOpponentListUseCase,
    private val getOpponentDetailsUseCase: GetOpponentDetailsUseCase,
    private val createOpponentUseCase: CreateOpponentUseCase,
    private val updateOpponentUseCase: UpdateOpponentUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(OpponentFormUiState())
    val state: StateFlow<OpponentFormUiState> = _state.asStateFlow()

    // The opponent just created or saved.
    private val _done = MutableSharedFlow<Opponent>()
    val done: SharedFlow<Opponent> = _done.asSharedFlow()

    private var clubId: String? = null
    private var ageGroupId: String? = null
    // Set when editing.
    private var editing: Opponent? = null
    // The club's opponents, for the duplicate-name check before submitting. Best effort: without
    // them the server still refuses a duplicate.
    private var clubOpponents: List<Opponent> = emptyList()

    fun start(clubId: String, ageGroupId: String?, initialName: String) {
        if (this.clubId != null) return
        this.clubId = clubId
        this.ageGroupId = ageGroupId
        _state.update { it.copy(form = it.form.copy(name = initialName)) }
        viewModelScope.launch {
            try {
                val list = getOpponentListUseCase(clubId, ageGroupId)
                clubOpponents = list.opponents
                _state.update { it.copy(clubName = list.clubName) }
            } catch (e: Exception) {
                // The form works without it.
            }
        }
    }

    /** Edit: loads the opponent into the form. [force] retries after a failed load. */
    fun startEdit(opponentId: String, force: Boolean = false) {
        if (editing != null || (!force && _state.value.loading)) return
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            val details = try {
                getOpponentDetailsUseCase(opponentId)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true) }
                return@launch
            }
            val opponent = details.opponent
            editing = opponent
            clubId = opponent.clubId
            val form = opponent.toForm()
            _state.update { it.copy(clubName = details.clubName, form = form, original = form, loading = false) }
            try {
                clubOpponents = getOpponentListUseCase(opponent.clubId).opponents
            } catch (e: Exception) {
                // The server still refuses a duplicate.
            }
        }
    }

    fun onFormChange(form: OpponentForm) = _state.update {
        val nameChanged = form.name != it.form.name
        it.copy(
            form = form.copy(shortName = typedShortName(form.shortName)),
            duplicateName = if (nameChanged) false else it.duplicateName,
            error = null
        )
    }

    fun onNameFocusLost() = _state.update {
        it.copy(duplicateName = isDuplicateOpponentName(it.form.name, clubOpponents, exceptId = editing?.id))
    }

    fun onSubmit() {
        val clubId = clubId ?: return
        val current = _state.value
        if (!current.canSubmit) return
        if (isDuplicateOpponentName(current.form.name, clubOpponents, exceptId = editing?.id)) {
            _state.update { it.copy(duplicateName = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            try {
                val edited = editing
                val opponent = if (edited != null) {
                    updateOpponentUseCase(edited.id, current.form.toUpdateRequest())
                    edited
                } else {
                    createOpponentUseCase(clubId, current.form.toCreateRequest(ageGroupId))
                }
                _done.emit(opponent)
            } catch (e: Exception) {
                if (e is ApiException && e.statusCode == HttpStatusCode.Conflict.value) {
                    _state.update { it.copy(submitting = false, duplicateName = true) }
                } else {
                    val message = errorMessage(e, if (editing != null) Res.string.msg_opponent_save_failed else Res.string.msg_create_opponent_failed)
                    _state.update { it.copy(submitting = false, error = message) }
                }
            }
        }
    }
}
