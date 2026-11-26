package com.sportssoftw.squadcall

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.sportssoftw.squadcall.data.config.DefaultAppConfig
import com.sportssoftw.squadcall.di.initKoin

fun main() {
    val appConfig = DefaultAppConfig(
        baseUrl = SquadcallBuildConfig.BASE_URL,
        isDebug = SquadcallBuildConfig.IS_DEBUG
    )
    initKoin(appConfig = appConfig)

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Squadcall",
        ) {
            App()
        }
    }
}