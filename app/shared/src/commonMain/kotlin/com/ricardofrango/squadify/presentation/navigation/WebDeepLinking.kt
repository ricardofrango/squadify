package com.ricardofrango.squadify.presentation.navigation

/**
 * On web (js/wasmJs), binds the back stack to the browser's address and history: each screen gets its
 * own address ([toRoute], after a `#`), and the browser's Back and Forward move through the app instead
 * of leaving it. A no-op on every other platform, which has no address bar or history to sync.
 */
expect suspend fun AppNavigator.bindToBrowserHistoryIfWeb()

/**
 * The route the app was opened with, if any: the URL's `#...` fragment, still URI-encoded (as
 * [screenFromRoute] reads it). Reads it and clears it from the visible URL exactly once, synchronously,
 * so it's opened by AppNavigation's own (auth-gated) deep-link handling in `SplashViewModel` only once
 * the session check allows it, e.g. not for a link shared with someone who isn't logged in. Null on
 * every non-web platform, or when the URL had no fragment.
 */
expect fun initialDeepLinkRouteOrNull(): String?
