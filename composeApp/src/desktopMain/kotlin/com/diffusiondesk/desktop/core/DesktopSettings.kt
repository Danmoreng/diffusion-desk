package com.diffusiondesk.desktop.core

import java.io.File

data class DesktopSettings(
    val repoRoot: String,
    val listenPort: Int,
    val modelDir: String,
    val outputDir: String,
    val setupCompleted: Boolean,
)

fun detectDefaultRepoRoot(): String {
    var current = File(System.getProperty("user.dir")).absoluteFile
    repeat(6) {
        if (File(current, "settings.gradle.kts").exists() && File(current, "src").exists()) {
            return current.absolutePath
        }
        current = current.parentFile ?: return@repeat
    }
    return File(System.getProperty("user.dir")).absolutePath
}
