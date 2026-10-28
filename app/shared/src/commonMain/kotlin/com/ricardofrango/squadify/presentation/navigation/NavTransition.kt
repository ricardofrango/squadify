package com.ricardofrango.squadify.presentation.navigation

import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KClass

// A screen usually starts in Loading and gets its data while it is still sliding in (see the NavDisplay
// transitions in AppNavigation). Swapping the spinner for the full content composes and lays out the
// whole loaded tree in one frame, mid-slide, which shows as a hitch in the animation. Screens hold
// their state still while their transition runs and switch to the latest one when it settles.

/** False while this destination's enter/exit transition is running. Always true outside NavDisplay. */
val LocalNavTransitionSettled = compositionLocalOf<State<Boolean>> { mutableStateOf(true) }

/** NavEntry metadata: this entry is the splash screen, which the app leaves with a fade, not a slide. */
internal const val SPLASH_ENTRY = "squadify.splash"

/** Each screen type's content, for NavDisplay's entryProvider (AppNavigation's `screens { }`). */
class ScreenRegistry @PublishedApi internal constructor() {
    @PublishedApi
    internal val contents = mutableMapOf<KClass<out Screen>, @Composable (AppBackStackEntry) -> Unit>()

    /** The content of every screen of type [T]. */
    inline fun <reified T : Screen> screen(noinline content: @Composable (AppBackStackEntry) -> Unit) {
        contents[T::class] = content
    }

    /** The NavEntry for [entry]: its own content key (its id) and [LocalNavTransitionSettled]. */
    fun entryFor(entry: AppBackStackEntry): NavEntry<AppBackStackEntry> {
        val content = contents[entry.screen::class] ?: error("No screen registered for ${entry.screen::class.simpleName}")
        val metadata = if (entry.screen is Screen.Splash) mapOf(SPLASH_ENTRY to true) else emptyMap()
        return NavEntry(entry, contentKey = entry.id, metadata = metadata) { key ->
            val transition = LocalNavAnimatedContentScope.current.transition
            val settled = remember(transition) {
                derivedStateOf {
                    transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
                }
            }
            CompositionLocalProvider(LocalNavTransitionSettled provides settled) {
                content(key)
            }
        }
    }
}

/** Registers every screen's content: `screens { screen<Screen.Login> { entry -> … } }`. */
inline fun screens(block: ScreenRegistry.() -> Unit): ScreenRegistry = ScreenRegistry().apply(block)

private class Held<T>(var value: T)

/**
 * Collects a screen's UI state, but while the screen's navigation transition is running keeps
 * returning the value it had when the transition started. Once settled it is a plain pass-through
 * (synchronous, so text fields bound to the state never lag behind).
 */
@Composable
fun <T> StateFlow<T>.collectSettledState(): T {
    val latest = collectAsStateWithLifecycle().value
    val settled = LocalNavTransitionSettled.current.value
    val held = remember { Held(latest) }
    if (settled) held.value = latest
    return held.value
}
