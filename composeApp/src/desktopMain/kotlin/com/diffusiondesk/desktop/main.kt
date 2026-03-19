package com.diffusiondesk.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    var darkTheme by remember { mutableStateOf(true) }
    val controller = remember { AppController() }
    val windowState = rememberWindowState(width = 1440.dp, height = 920.dp)

    Window(
        onCloseRequest = {
            controller.close()
            exitApplication()
        },
        title = "Diffusion Desk",
        icon = painterResource("icons/app-icon.png"),
        state = windowState,
    ) {
        App(
            controller = controller,
            darkTheme = darkTheme,
            onToggleTheme = { darkTheme = !darkTheme },
        )
    }
}
