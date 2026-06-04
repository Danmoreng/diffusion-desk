plugins {
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
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
