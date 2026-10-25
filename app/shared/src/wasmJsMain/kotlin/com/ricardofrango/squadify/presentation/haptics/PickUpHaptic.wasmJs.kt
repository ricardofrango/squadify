package com.ricardofrango.squadify.presentation.haptics

import androidx.compose.runtime.Composable

// The Vibration API: Android browsers vibrate, others (iOS Safari, desktop) ignore it.
private fun vibrate(ms: Int): Unit = js("{ if (navigator.vibrate) navigator.vibrate(ms); }")

@Composable
actual fun rememberPickUpHaptic(): () -> Unit = { runCatching { vibrate(30) } }
