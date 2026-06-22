package com.diffusiondesk.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val controller = remember { AppController() }
    val windowState = rememberWindowState(width = 1440.dp, height = 920.dp)

    Window(
        onCloseRequest = {
            controller.close()
            exitApplication()
        },
        title = "Diffusion Desk",
        icon = painterResource("icons/app-icon.svg"),
        state = windowState,
    ) {
        App(controller = controller)
    }
}
