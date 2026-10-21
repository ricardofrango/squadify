package com.ricardofrango.squadify.presentation.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.coroutines.flow.StateFlow

// A screen usually starts in Loading and gets its data while it is still sliding in (see the NavHost
// transitions in AppNavigation). Swapping the spinner for the full content composes and lays out the
// whole loaded tree in one frame, mid-slide, which shows as a hitch in the animation. Screens hold
// their state still while their transition runs and switch to the latest one when it settles.

/** False while this destination's enter/exit transition is running. Always true outside a NavHost. */
val LocalNavTransitionSettled = compositionLocalOf<State<Boolean>> { mutableStateOf(true) }

/** `composable<T>` that also provides [LocalNavTransitionSettled] to the destination's content, and draws it in its club's colours ([ClubThemedEntry]). */
inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable<T> { entry ->
        val settled = remember(transition) {
            derivedStateOf {
                transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
            }
        }
        CompositionLocalProvider(LocalNavTransitionSettled provides settled) {
            ClubThemedEntry(entry) { content(entry) }
        }
    }
}

private class Held<T>(var value: T)

/**
 * Collects a screen's UI state, but while the screen's navigation transition is running keeps
 * returning the value it had when the transition started. Once settled it is a plain pass-through
 * (synchronous, so text fields bound to the state never lag behind).
 */
@Composable
fun <T> StateFlow<T>.collectSettledState(): T {
    val latest = collectAsState().value
    val settled = LocalNavTransitionSettled.current.value
    val held = remember { Held(latest) }
    if (settled) held.value = latest
    return held.value
}
