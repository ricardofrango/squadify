package com.ricardofrango.squadify.presentation.navigation

import androidx.navigation.NavHostController

/**
 * On web (js/wasmJs), binds this nav controller's back stack to the browser's actual URL and history --
 * each screen gets its own address, and the browser's Back/Forward buttons navigate within the app
 * instead of leaving it entirely (there was previously only ever one history entry for the whole
 * session -- see TECHNICAL_DECISIONS.md). A no-op on every other platform, which has no address bar or
 * history to sync in the first place.
 */
expect suspend fun NavHostController.bindToBrowserHistoryIfWeb()

/**
 * The route the app was opened with, if any -- the URL's `#...` fragment. Reads it and clears it from
 * the visible URL exactly once, synchronously, so it's consumed by [AppNavigation]'s own (auth-gated)
 * deep-link handling in `SplashViewModel` rather than by [bindToBrowserHistoryIfWeb]'s own default
 * behavior, which would otherwise try to navigate straight to it immediately -- before the session check
 * has had a chance to resolve, e.g. for a link shared with someone who isn't logged in. Null on every
 * non-web platform, or when the URL had no fragment.
 */
expect fun initialDeepLinkRouteOrNull(): String?
