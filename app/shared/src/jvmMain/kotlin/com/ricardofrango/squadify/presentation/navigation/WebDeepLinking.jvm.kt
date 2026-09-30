package com.ricardofrango.squadify.presentation.navigation

import androidx.navigation.NavHostController

// No address bar to sync on Desktop.
actual suspend fun NavHostController.bindToBrowserHistoryIfWeb() = Unit

actual fun initialDeepLinkRouteOrNull(): String? = null
