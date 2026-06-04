import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

fun detectPowerShellExecutable(): String {
    return try {
        val process = ProcessBuilder("where", "pwsh.exe")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode == 0 && output.isNotBlank()) "pwsh.exe" else "powershell.exe"
    } catch (_: Exception) {
        "powershell.exe"
    }
}

fun Exec.configurePowerShellScript(scriptRelativePath: String, vararg scriptArgs: String) {
    doFirst {
        if (!org.gradle.internal.os.OperatingSystem.current().isWindows) {
            throw GradleException("Windows package scripts are only supported on Windows.")
        }
        val shell = detectPowerShellExecutable()
        val scriptFile = project.layout.projectDirectory.file(scriptRelativePath).asFile
        commandLine(
            shell,
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            scriptFile.absolutePath,
            *scriptArgs,
        )
        workingDir = project.layout.projectDirectory.asFile
    }
}

tasks.register("desktopRun") {
    group = "application"
    description = "Run the Compose desktop shell."
    dependsOn(":composeApp:run")
}

tasks.register("desktopPackage") {
    group = "distribution"
    description = "Create a distributable image for the Compose desktop shell."
    dependsOn(":composeApp:createDistributable")
}

tasks.register<Exec>("packageWindows") {
    group = "distribution"
    description = "Build Windows portable Compose app package."
    configurePowerShellScript("scripts/package-windows.ps1")
}

tasks.register<Exec>("packageWindowsMsi") {
    group = "distribution"
    description = "Build Windows portable Compose app package and MSI installer."
    configurePowerShellScript("scripts/package-windows.ps1", "-BuildMsi")
}
