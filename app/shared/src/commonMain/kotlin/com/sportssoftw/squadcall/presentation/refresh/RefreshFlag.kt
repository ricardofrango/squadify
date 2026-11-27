package com.sportssoftw.squadcall.presentation.refresh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a pull-to-refresh is running: a view model shows the pull indicator from [running]. A refresh reads the
 * server (`ForceRefresh`, TECHNICAL_DECISIONS §83) and runs [during] its reload.
 */
class RefreshFlag {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Runs [reload] with the indicator up, and down again however it ends. */
    suspend fun during(reload: suspend () -> Unit) {
        _running.value = true
        try {
            reload()
        } finally {
            _running.value = false
        }
    }
}
