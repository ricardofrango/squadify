package com.ricardofrango.squadify.presentation.haptics

import androidx.compose.runtime.Composable

/**
 * A short, firm vibration for picking something up to drag (a player on the pitch). It vibrates
 * directly rather than through Compose's haptic feedback, which on Android follows the system's
 * "touch feedback" setting and is too faint, or off, on many phones. Nothing where there's no
 * vibration (desktop, a browser without it).
 */
@Composable
expect fun rememberPickUpHaptic(): () -> Unit
