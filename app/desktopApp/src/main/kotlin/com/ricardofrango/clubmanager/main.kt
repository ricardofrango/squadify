package com.ricardofrango.clubmanager

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ClubManager",
    ) {
        App()
    }
}