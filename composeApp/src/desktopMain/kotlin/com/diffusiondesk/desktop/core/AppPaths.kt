package com.diffusiondesk.desktop.core

import java.io.File

object AppPaths {
    val appDir: File by lazy {
        val osName = System.getProperty("os.name").lowercase()
        when {
            osName.contains("win") -> {
                val appData = System.getenv("APPDATA")
                File(appData ?: System.getProperty("user.home"), "DiffusionDesk")
            }
            osName.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support/DiffusionDesk")
            else -> File(System.getenv("XDG_CONFIG_HOME") ?: File(System.getProperty("user.home"), ".config").absolutePath, "diffusion-desk")
        }
    }
}
