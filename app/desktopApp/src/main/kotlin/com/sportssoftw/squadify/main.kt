package com.sportssoftw.squadify

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.sportssoftw.squadify.data.config.DefaultAppConfig
import com.sportssoftw.squadify.di.initKoin

fun main() {
    val appConfig = DefaultAppConfig(
        baseUrl = SquadifyBuildConfig.BASE_URL,
        isDebug = SquadifyBuildConfig.IS_DEBUG
    )
    initKoin(appConfig = appConfig)

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Squadify",
        ) {
            App()
        }
    }
}