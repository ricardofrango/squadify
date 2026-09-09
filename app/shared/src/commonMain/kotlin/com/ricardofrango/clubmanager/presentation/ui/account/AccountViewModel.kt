package com.ricardofrango.clubmanager.presentation.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.clubmanager.domain.usecase.LogoutUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AccountViewModel(
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<AccountUiState>(AccountUiState.Idle)
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<AccountNavigationEvent>()
    val navigationEvent: SharedFlow<AccountNavigationEvent> = _navigationEvent.asSharedFlow()

    fun onLogoutClicked() {
        viewModelScope.launch {
            logoutUseCase()
            _navigationEvent.emit(AccountNavigationEvent.NavigateToLogin)
        }
    }
}
