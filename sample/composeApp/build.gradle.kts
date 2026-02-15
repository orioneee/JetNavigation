import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.android.application)
}

kotlin {
    jvmToolchain(21)

    androidTarget()
    jvm()
    wasmJs {
        browser()
        binaries.executable()
    }
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.ui)
            implementation(libs.foundation)
            implementation(libs.jetbrains.material3)

            implementation(project(":JetNavigation"))
        }

        androidMain.dependencies {
            implementation(libs.androidx.activityCompose)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs) {
                exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-macos-arm64")
            }
            implementation("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:0.9.37.4")
        }

    }
}

android {
    namespace = "sample.app"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        targetSdk = 36

        applicationId = "sample.app.androidApp"
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }
    buildTypes {
        debug {
            isDebuggable = false
            isMinifyEnabled = true
        }
        release {
            isMinifyEnabled = true
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "sample"
            packageVersion = "1.0.0"
        }
        jvmArgs += listOf(
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED"
        )
    }
}
