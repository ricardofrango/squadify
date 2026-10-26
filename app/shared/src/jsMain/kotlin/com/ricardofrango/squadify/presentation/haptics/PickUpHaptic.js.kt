package com.ricardofrango.squadify.presentation.haptics

import androidx.compose.runtime.Composable
import kotlinx.browser.window

// The Vibration API: Android browsers vibrate, others (iOS Safari, desktop) ignore it.
@Composable
actual fun rememberPickUpHaptic(): () -> Unit = {
    runCatching { window.navigator.asDynamic().vibrate(30) }
}
