package com.ricardofrango.squadify.presentation.navigation

// No address bar to sync on Android.
actual suspend fun AppNavigator.bindToBrowserHistoryIfWeb() = Unit

actual fun initialDeepLinkRouteOrNull(): String? = null
