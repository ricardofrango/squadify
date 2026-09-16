package com.ricardofrango.squadify

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.ricardofrango.squadify.data.config.DefaultAppConfig
import com.ricardofrango.squadify.di.initKoin

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