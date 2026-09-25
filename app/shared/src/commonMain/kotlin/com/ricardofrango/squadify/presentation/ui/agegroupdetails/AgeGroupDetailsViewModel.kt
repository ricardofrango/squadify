package com.ricardofrango.squadify.presentation.ui.agegroupdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupDetailsUseCase
import com.ricardofrango.squadify.presentation.errorMessage
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_failed_to_load_age_group
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AgeGroupDetailsViewModel(
    private val getAgeGroupDetailsUseCase: GetAgeGroupDetailsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<AgeGroupDetailsUiState>(AgeGroupDetailsUiState.Loading)
    val state: StateFlow<AgeGroupDetailsUiState> = _state.asStateFlow()

    fun load(ageGroupId: String, forceReload: Boolean = false) {
        if (!forceReload && _state.value is AgeGroupDetailsUiState.Idle) return

        viewModelScope.launch {
            _state.update { AgeGroupDetailsUiState.Loading }
            try {
                _state.update { AgeGroupDetailsUiState.Idle(getAgeGroupDetailsUseCase(ageGroupId)) }
            } catch (e: Exception) {
                val message = errorMessage(e, Res.string.msg_failed_to_load_age_group)
                _state.update { AgeGroupDetailsUiState.Error(message) }
            }
        }
    }
}
