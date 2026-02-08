@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.buildConfig)
}

kotlin {
    jvmToolchain(21)

    androidTarget { publishLibraryVariants("release") }
    jvm()
    wasmJs { browser() }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.ui)
            implementation(libs.foundation)
            implementation(libs.jetbrains.material3)
            implementation(libs.material.icons.extended)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.multiplatformSettings)
            implementation(libs.components.resources)

            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.navigation.compose)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)


        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.android.ndk27)
            implementation(libs.maps.compose.ndk27)
            implementation(libs.androidx.startup.runtime)
//            implementation(libs.maps.compose)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.compose.webview.multiplatform)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

    }

    //https://kotlinlang.org/docs/native-objc-interop.html#export-of-kdoc-comments-to-generated-objective-c-headers
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        compilations["main"].compileTaskProvider.configure {
            compilerOptions {
                freeCompilerArgs.add("-Xexport-kdoc")
            }
        }
    }

}

android {
    namespace = "com.oriooneee.jet.navigation"
    compileSdk = 36

    defaultConfig {
        minSdk = 21

        val googleMapsApiKey = providers.provider {
            System.getenv("GOOGLE_MAPS_API_KEY")
                ?: rootProject.file("local.properties")
                    .takeIf { it.exists() }
                    ?.readLines()
                    ?.firstOrNull { it.startsWith("GOOGLE_MAPS_API_KEY=") }
                    ?.substringAfter("=")
                ?: ""
        }.getOrElse("")
//        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = googleMapsApiKey
    }
}

//Publishing your Kotlin Multiplatform library to Maven Central
//https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-publish-libraries.html
mavenPublishing {
    publishToMavenCentral()
    coordinates("com.oriooneee.jet.navigation", "JetNavigation", "1.0.0")

    pom {
        name = "JetNavigation"
        description = "Kotlin Multiplatform library"
        url = "github url" //todo

        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/licenses/MIT"
            }
        }

        developers {
            developer {
                id = "" //todo
                name = "" //todo
                email = "" //todo
            }
        }

        scm {
            url = "github url" //todo
        }
    }
    if (project.hasProperty("signing.keyId")) signAllPublications()
}

buildConfig {
    packageName("com.oriooneee.jet.navigation.buildconfig")

    val mapBoxApiKey = providers.provider {
        System.getenv("MAPBOX_API_KEY")
            ?: rootProject.file("local.properties")
                .takeIf { it.exists() }
                ?.readLines()
                ?.firstOrNull { it.startsWith("MAPBOX_API_KEY=") }
                ?.substringAfter("=")
            ?: ""
    }.getOrElse("")
    val googleMapsApiKey = providers.provider {
        System.getenv("GOOGLE_MAPS_API_KEY")
            ?: rootProject.file("local.properties")
                .takeIf { it.exists() }
                ?.readLines()
                ?.firstOrNull { it.startsWith("GOOGLE_MAPS_API_KEY=") }
                ?.substringAfter("=")
            ?: ""
    }.get()
    val apiKey = providers.provider {
        System.getenv("API_KEY")
            ?: rootProject.file("local.properties")
                .takeIf { it.exists() }
                ?.readLines()
                ?.firstOrNull { it.startsWith("API_KEY=") }
                ?.substringAfter("=")
            ?: ""
    }.get()
    val baseUrl = providers.provider {
        System.getenv("BASE_URL")
            ?: rootProject.file("local.properties")
                .takeIf { it.exists() }
                ?.readLines()
                ?.firstOrNull { it.startsWith("BASE_URL=") }
                ?.substringAfter("=")
    }.get()
    println("GOOGLE_MAPS_API_KEY is set: ${googleMapsApiKey.length}")
    println("MAPBOX_API_KEY is set: ${mapBoxApiKey.length}")
    println("API_KEY is set: ${apiKey.length}")

    buildConfigField("String", "MAPBOX_API_KEY", "\"$mapBoxApiKey\"")
//    buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"$googleMapsApiKey\"")
    buildConfigField("String", "API_KEY", "\"$apiKey\"")
    buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
}

