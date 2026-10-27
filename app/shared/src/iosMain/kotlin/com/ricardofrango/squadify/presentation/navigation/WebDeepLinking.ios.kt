package com.ricardofrango.squadify.presentation.navigation

// No address bar to sync on iOS.
actual suspend fun AppNavigator.bindToBrowserHistoryIfWeb() = Unit

actual fun initialDeepLinkRouteOrNull(): String? = null
