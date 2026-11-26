package com.sportssoftw.squadcall

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.sportssoftw.squadcall.data.config.DefaultAppConfig
import com.sportssoftw.squadcall.di.initKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val appConfig = DefaultAppConfig(
        baseUrl = SquadcallBuildConfig.BASE_URL,
        isDebug = SquadcallBuildConfig.IS_DEBUG
    )
    initKoin(appConfig = appConfig)

    ComposeViewport {
        App()
    }
}