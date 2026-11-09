package com.ricardofrango.squadify.presentation.ui.splash

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the app's [SplashScreen] is still up, so a platform's own launch splash can stay on screen
 * until it's gone and the app opens straight onto Login or Home: one splash, the system's (Android's
 * activities hold theirs with it). [start] when the app's screens start (not after a rotation), [finish]
 * once the splash route has left.
 */
object LaunchSplash {
    private val showing = MutableStateFlow(true)
    val isShowing: StateFlow<Boolean> = showing.asStateFlow()

    fun start() {
        showing.value = true
    }

    fun finish() {
        showing.value = false
    }
}
