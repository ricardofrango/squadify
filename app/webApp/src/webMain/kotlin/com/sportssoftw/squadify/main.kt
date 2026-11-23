package com.sportssoftw.squadify

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.sportssoftw.squadify.data.config.DefaultAppConfig
import com.sportssoftw.squadify.di.initKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val appConfig = DefaultAppConfig(
        baseUrl = SquadifyBuildConfig.BASE_URL,
        isDebug = SquadifyBuildConfig.IS_DEBUG
    )
    initKoin(appConfig = appConfig)

    ComposeViewport {
        App()
    }
}