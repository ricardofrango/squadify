package com.ricardofrango.squadify.presentation.ui.createstaff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.AddClubMembersUseCase
import com.ricardofrango.squadify.model.AddClubMembersRequest
import com.ricardofrango.squadify.presentation.errorMessage
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_create_staff_failed
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateStaffViewModel(
    private val addClubMembersUseCase: AddClubMembersUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(CreateStaffUiState())
    val state: StateFlow<CreateStaffUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<CreateStaffNavigationEvent>()
    val navigationEvent: SharedFlow<CreateStaffNavigationEvent> = _navigationEvent.asSharedFlow()

    fun clearSubmitError() {
        _state.update { it.copy(error = null) }
    }

    fun onCreateClicked(clubId: String, emails: List<String>) {
        if (_state.value.isSubmitting || emails.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            try {
                addClubMembersUseCase(clubId, AddClubMembersRequest(emails))
                _navigationEvent.emit(CreateStaffNavigationEvent.NavigateBackWithStaffCreated)
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_create_staff_failed)
                _state.update { it.copy(isSubmitting = false, error = message) }
            }
        }
    }
}
