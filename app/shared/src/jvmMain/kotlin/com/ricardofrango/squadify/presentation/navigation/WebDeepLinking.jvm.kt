package com.ricardofrango.squadify.presentation.navigation

// No address bar to sync on Desktop.
actual suspend fun AppNavigator.bindToBrowserHistoryIfWeb() = Unit

actual fun initialDeepLinkRouteOrNull(): String? = null
