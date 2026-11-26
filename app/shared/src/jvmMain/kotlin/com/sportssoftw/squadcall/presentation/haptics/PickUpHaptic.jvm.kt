package com.sportssoftw.squadcall.presentation.haptics

import androidx.compose.runtime.Composable

// A desktop has nothing to vibrate.
@Composable
actual fun rememberPickUpHaptic(): () -> Unit = {}
