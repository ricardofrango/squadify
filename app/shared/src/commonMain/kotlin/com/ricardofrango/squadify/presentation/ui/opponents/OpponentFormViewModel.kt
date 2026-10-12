package com.ricardofrango.squadify.presentation.ui.opponents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.domain.usecase.CreateOpponentUseCase
import com.ricardofrango.squadify.domain.usecase.GetOpponentListUseCase
import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.OPPONENT_SHORT_NAME_MAX
import com.ricardofrango.squadify.model.Opponent
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
    // "An opponent with this name already exists": checked when Name loses focus and on submit.
    val duplicateName: Boolean = false,
    val submitting: Boolean = false,
    // Localized copy only, never a raw exception message (AI_GUIDELINES.md).
    val error: String? = null
) {
    val canSubmit: Boolean get() = form.name.isNotBlank() && !duplicateName && !submitting
}

/** Whether [name] is already one of [opponents]' names, ignoring case and surrounding spaces. */
internal fun isDuplicateOpponentName(name: String, opponents: List<Opponent>, exceptId: String? = null): Boolean {
    val wanted = name.trim()
    return wanted.isNotEmpty() && opponents.any { it.id != exceptId && it.name.trim().equals(wanted, ignoreCase = true) }
}

/** The short name as it's typed: uppercase and at most [OPPONENT_SHORT_NAME_MAX] characters. */
internal fun typedShortName(value: String): String = value.uppercase().take(OPPONENT_SHORT_NAME_MAX)

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

/** Create opponent (design 38e). */
class OpponentFormViewModel(
    private val getOpponentListUseCase: GetOpponentListUseCase,
    private val createOpponentUseCase: CreateOpponentUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(OpponentFormUiState())
    val state: StateFlow<OpponentFormUiState> = _state.asStateFlow()

    private val _created = MutableSharedFlow<Opponent>()
    val created: SharedFlow<Opponent> = _created.asSharedFlow()

    private var clubId: String? = null
    private var ageGroupId: String? = null
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

    fun onFormChange(form: OpponentForm) = _state.update {
        val nameChanged = form.name != it.form.name
        it.copy(
            form = form.copy(shortName = typedShortName(form.shortName)),
            duplicateName = if (nameChanged) false else it.duplicateName,
            error = null
        )
    }

    fun onNameFocusLost() = _state.update { it.copy(duplicateName = isDuplicateOpponentName(it.form.name, clubOpponents)) }

    fun onSubmit() {
        val clubId = clubId ?: return
        val current = _state.value
        if (current.form.name.isBlank() || current.submitting) return
        if (isDuplicateOpponentName(current.form.name, clubOpponents)) {
            _state.update { it.copy(duplicateName = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            try {
                val opponent = createOpponentUseCase(clubId, current.form.toCreateRequest(ageGroupId))
                _created.emit(opponent)
            } catch (e: Exception) {
                if (e is ApiException && e.statusCode == HttpStatusCode.Conflict.value) {
                    _state.update { it.copy(submitting = false, duplicateName = true) }
                } else {
                    val message = errorMessage(e, Res.string.msg_create_opponent_failed)
                    _state.update { it.copy(submitting = false, error = message) }
                }
            }
        }
    }
}
