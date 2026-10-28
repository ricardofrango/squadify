package com.ricardofrango.squadify.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// The app's back stack, owned by the app as Navigation 3 has it (TECHNICAL_DECISIONS.md §11.4): a list
// of entries that NavDisplay (AppNavigation) shows the top of. The calls mirror the NavController ones
// the screens were written against (navigate with popUpTo, popBackStack, the previous entry's
// savedStateHandle for a result), so moving to Navigation 3 changed how the stack is held, not how
// each screen navigates.

/**
 * A screen's results: values another screen leaves for it (a "something changed, reload" flag, a
 * message to show), read as state. What NavBackStackEntry.savedStateHandle was for in Navigation 2,
 * and saved with the back stack in the same way.
 */
class EntryResults internal constructor(initial: Map<String, Any?> = emptyMap()) {
    private val values = initial.mapValues { (_, v) -> MutableStateFlow(v) }.toMutableMap()

    operator fun set(key: String, value: Any?) {
        values.getOrPut(key) { MutableStateFlow(value) }.value = value
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: String): T? = values[key]?.value as T?

    @Suppress("UNCHECKED_CAST")
    fun <T> getStateFlow(key: String, initialValue: T): StateFlow<T> =
        values.getOrPut(key) { MutableStateFlow(initialValue) } as StateFlow<T>

    internal fun snapshot(): Map<String, Any?> = values.mapValues { it.value.value }
}

/**
 * One screen in the back stack. [id] tells two entries of the same screen apart: each keeps its own
 * remembered state and view models (NavDisplay's content key), as each NavBackStackEntry did.
 */
class AppBackStackEntry internal constructor(
    val id: String,
    val screen: Screen,
    results: Map<String, Any?> = emptyMap()
) {
    val savedStateHandle = EntryResults(results)
}

/** The route of this entry, as [Screen]'s subtype: `val screen: Screen.TeamDetails = entry.toRoute()`. */
@Suppress("UNCHECKED_CAST")
fun <T : Screen> AppBackStackEntry.toRoute(): T = screen as T

/** Where `navigate` pops back to before adding the new screen, as NavOptions' popUpTo. */
class NavOptionsBuilder internal constructor() {
    internal var popUpTo: ((Screen) -> Boolean)? = null
    internal var popAll = false
    internal var inclusive = false

    class PopUpToBuilder internal constructor() {
        var inclusive: Boolean = false
    }

    /** Back to the topmost [screen] (by equality), and that one too when inclusive. */
    fun popUpTo(screen: Screen, block: PopUpToBuilder.() -> Unit = {}) = setPopUpTo({ it == screen }, block)

    /** Back to the topmost screen of type [T], and that one too when inclusive. */
    inline fun <reified T : Screen> popUpTo(noinline block: PopUpToBuilder.() -> Unit = {}) = popUpToMatching({ it is T }, block)

    @PublishedApi
    internal fun popUpToMatching(match: (Screen) -> Boolean, block: PopUpToBuilder.() -> Unit) = setPopUpTo(match, block)

    /** Clears the whole back stack: the new screen is the only one. */
    fun popUpToRoot() {
        popAll = true
    }

    private fun setPopUpTo(match: (Screen) -> Boolean, block: PopUpToBuilder.() -> Unit) {
        popUpTo = match
        inclusive = PopUpToBuilder().apply(block).inclusive
    }
}

@OptIn(ExperimentalUuidApi::class)
class AppNavigator internal constructor(entries: List<AppBackStackEntry>) {
    /** The back stack, bottom first. Never empty. */
    val backStack: SnapshotStateList<AppBackStackEntry> = entries.toMutableStateList()

    val currentBackStackEntry: AppBackStackEntry? get() = backStack.lastOrNull()
    val previousBackStackEntry: AppBackStackEntry? get() = backStack.getOrNull(backStack.lastIndex - 1)

    fun navigate(screen: Screen, options: NavOptionsBuilder.() -> Unit = {}) {
        val built = NavOptionsBuilder().apply(options)
        val kept = when {
            built.popAll -> emptyList()
            built.popUpTo != null -> {
                val index = backStack.indexOfLast { built.popUpTo!!(it.screen) }
                when {
                    index < 0 -> backStack.toList() // not in the stack: nothing popped, as in Navigation 2
                    built.inclusive -> backStack.subList(0, index).toList()
                    else -> backStack.subList(0, index + 1).toList()
                }
            }
            else -> backStack.toList()
        }
        replaceAll(kept + newEntry(screen))
    }

    /** Removes the top screen. False, and nothing popped, when it's the only one left. */
    fun popBackStack(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    /** Back to the topmost screen of type [T] (popped too when [inclusive]). False when there's none. */
    inline fun <reified T : Screen> popBackStack(inclusive: Boolean): Boolean = popBackStackTo({ it is T }, inclusive)

    @PublishedApi
    internal fun popBackStackTo(match: (Screen) -> Boolean, inclusive: Boolean): Boolean {
        val index = backStack.indexOfLast { match(it.screen) }
        if (index < 0) return false
        val keep = if (inclusive) index else index + 1
        if (keep == 0) return false // never leave the stack empty
        replaceAll(backStack.subList(0, keep).toList())
        return true
    }

    /** The topmost entry for a screen of type [T]; throws IllegalArgumentException when there's none. */
    inline fun <reified T : Screen> getBackStackEntry(): AppBackStackEntry =
        backStack.lastOrNull { it.screen is T } ?: throw IllegalArgumentException("No ${T::class.simpleName} in the back stack")

    /** Replaces the whole back stack with [screens], bottom first. */
    fun openBackStack(screens: List<Screen>) {
        if (screens.isEmpty()) return
        replaceAll(screens.map(::newEntry))
    }

    /**
     * Shows [screens], bottom first, keeping the entries (and their state) of the part that's the same
     * as now. Used by the browser's Back and Forward.
     */
    fun restoreBackStack(screens: List<Screen>) {
        if (screens.isEmpty()) return
        val common = backStack.zip(screens).takeWhile { (entry, screen) -> entry.screen == screen }.size
        replaceAll(backStack.subList(0, common).toList() + screens.drop(common).map(::newEntry))
    }

    /** Still in the back stack: a result left for it will be read. */
    fun isAlive(entry: AppBackStackEntry): Boolean = backStack.any { it === entry }

    private fun newEntry(screen: Screen) = AppBackStackEntry(Uuid.random().toString(), screen)

    private fun replaceAll(entries: List<AppBackStackEntry>) {
        // One change, so NavDisplay sees a single move (and animates it once).
        if (entries == backStack.toList()) return
        backStack.clear()
        backStack.addAll(entries)
    }

    companion object {
        fun startingAt(screen: Screen) = AppNavigator(listOf(AppBackStackEntry(Uuid.random().toString(), screen)))

        /**
         * Saves the back stack, each entry's results included, as one JSON string: the routes through
         * [Screen]'s own serializer, so it works the same on every platform (no reflection on iOS or web).
         */
        val Saver: Saver<AppNavigator, String> = Saver(
            save = { navigator ->
                json.encodeToString(ListSerializer(SavedEntry.serializer()), navigator.backStack.map { entry ->
                    SavedEntry(entry.id, entry.screen, entry.savedStateHandle.snapshot().mapValues { (_, v) -> v.toJson() })
                })
            },
            restore = { saved ->
                val entries = json.decodeFromString(ListSerializer(SavedEntry.serializer()), saved)
                    .map { AppBackStackEntry(it.id, it.screen, it.results.mapValues { (_, v) -> v.fromJson() }) }
                if (entries.isEmpty()) null else AppNavigator(entries)
            }
        )

        private val json = Json { ignoreUnknownKeys = true }
    }
}

/** The app's navigator, kept across configuration changes and process death. */
@Composable
fun rememberAppNavigator(start: Screen = Screen.Splash): AppNavigator =
    rememberSaveable(saver = AppNavigator.Saver) { AppNavigator.startingAt(start) }

@Serializable
private class SavedEntry(val id: String, val screen: Screen, val results: Map<String, JsonPrimitive> = emptyMap())

// Results are flags, messages and counts: booleans, strings and numbers.
private fun Any?.toJson(): JsonPrimitive = when (this) {
    null -> JsonNull
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    else -> JsonPrimitive(toString())
}

private fun JsonPrimitive.fromJson(): Any? = when {
    this is JsonNull -> null
    isString -> content
    else -> booleanOrNull ?: intOrNull ?: longOrNull ?: doubleOrNull
}
