package com.ricardofrango.squadify

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ricardofrango.squadify.data.config.DefaultAppConfig
import com.ricardofrango.squadify.di.initKoin

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