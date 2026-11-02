package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleStartEffect

/**
 * Runs [onReturn] each time the screen is shown again after being stopped: the app back from the
 * background, or the screen back on top. Not when it first opens, which loads by itself. For lists
 * with no live socket, so what changed while away (a game started, a result in) shows on return.
 */
@Composable
fun OnReturnToApp(onReturn: () -> Unit) {
    val latest by rememberUpdatedState(onReturn)
    val stopped = remember { BooleanArray(1) }
    LifecycleStartEffect(Unit) {
        if (stopped[0]) {
            stopped[0] = false
            latest()
        }
        onStopOrDispose { stopped[0] = true }
    }
}
