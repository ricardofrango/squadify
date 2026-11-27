package com.sportssoftw.squadcall

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.sportssoftw.squadcall.data.config.DefaultAppConfig
import com.sportssoftw.squadcall.di.initKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // The container's API_BASE_URL wins; the build's address is the fallback (e.g. local development).
    val appConfig = DefaultAppConfig(
        baseUrl = runtimeApiBaseUrl()?.takeIf { it.isNotBlank() } ?: SquadcallBuildConfig.BASE_URL,
        isDebug = SquadcallBuildConfig.IS_DEBUG
    )
    initKoin(appConfig = appConfig)

    ComposeViewport {
        App()
    }
}
