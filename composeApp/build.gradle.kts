import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val hostOs = System.getProperty("os.name").lowercase()
val nativeTargetFormats = when {
    hostOs.contains("win") -> arrayOf(TargetFormat.Msi)
    hostOs.contains("mac") -> arrayOf(TargetFormat.Dmg)
    else -> arrayOf(TargetFormat.Deb)
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.compose.compiler)
}

val appVersion = providers.gradleProperty("appVersion")
    .orElse(providers.environmentVariable("APP_VERSION"))
    .orElse("1.0.0")

kotlin {
    jvmToolchain(25)

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget("25"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(libs.jewel.int.ui.standalone)
            implementation(libs.intellij.platform.icons)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.sqlite.jdbc)
        }

        val desktopTest by getting
        desktopTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.sqlite.jdbc)
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        },
    )
}

compose.desktop {
    application {
        mainClass = "com.diffusiondesk.desktop.MainKt"

        nativeDistributions {
            targetFormats(*nativeTargetFormats)
            packageName = "diffusion-desk"
            packageVersion = appVersion.get()

            windows {
                iconFile.set(project.file("src/desktopMain/resources/icons/app-icon.ico"))
            }

            linux {
                iconFile.set(project.file("src/desktopMain/resources/icons/app-icon.png"))
            }

            modules(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.naming",
                "java.net.http",
                "java.sql",
                "jdk.unsupported",
                "jdk.security.auth",
            )
        }
    }
}
