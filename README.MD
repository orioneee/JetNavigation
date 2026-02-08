<div align="center">

# 🧭 JetNavigation

**Kotlin Multiplatform library for hybrid indoor/outdoor navigation and routing**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.x-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.7.x-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/compose-multiplatform/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20Desktop%20%7C%20Web-blue)]()

*Navigate between buildings, floors, and outdoors — all in one solution*

</div>

---

## 📖 Table of Contents

- [About](#-about)
- [Features](#-features)
- [Supported Platforms](#-supported-platforms)
- [Installation](#-installation)
- [Quick Start](#-quick-start)
- [Configuration](#-configuration)
- [Running the Sample App](#-running-the-sample-app)
- [Publishing](#-publishing)
- [Tech Stack](#-tech-stack)
- [License](#-license)

---

## 🎯 About

**JetNavigation** is a cross-platform Kotlin Multiplatform library that solves the problem of **hybrid navigation**: seamless routing inside buildings (with support for multiple floors and buildings) and outdoors with GPS map integration.

### Why do you need this?

Imagine a university campus, business center, or shopping mall — a user needs to get from Room 305 in Building A to Room 112 in Building B. JetNavigation will build the route:

1. 🏢 Navigate through the floor inside Building A
2. 🔽 Go down to the 1st floor
3. 🚶 Exit to the outdoors
4. 🗺️ Outdoor route to Building B
5. 🏢 Enter Building B and navigate to the destination room

---

## ✨ Features

| Feature | Description |
|---|---|
| 🏗️ **Multi-floor Navigation** | Support for buildings with multiple floors, stairway transitions |
| 🏘️ **Multi-building Navigation** | Routes between multiple buildings via outdoor paths |
| 🔄 **Hybrid Transitions** | Seamless indoor ↔ outdoor transitions |
| 📍 **Multiple Route Options** | Up to 4 alternative routes with time and distance estimates |
| 🗺️ **Interactive Maps** | Zoomable floor plans + Google Maps / MapBox |
| 🎨 **Animated Transitions** | Smooth animations between floors and buildings |
| 📱 **Cross-platform** | Single codebase for Android, iOS, Desktop, and Web |

---

## 📱 Supported Platforms

| Platform | Status | Minimum Version |
|---|---|---|
| 🤖 Android | ✅ | API 21 (Android 5.0) |
| 🍎 iOS | ✅ | iOS 13+ |
| 🖥️ Desktop (JVM) | ✅ | Java 21 |
| 🌐 Web (WASM) | ✅ | Modern browsers |

---

## 📦 Installation

### Gradle (Kotlin DSL)

```kotlin
// settings.gradle.kts
repositories {
    mavenCentral()
}

// build.gradle.kts
dependencies {
    implementation("com.oriooneee.jet.navigation:JetNavigation:1.0.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.oriooneee.jet.navigation:JetNavigation:1.0.0'
}
```

---

## 🚀 Quick Start

### Minimal Example

```kotlin
import com.oriooneee.jet.navigation.presentation.navigation.NavigationApp

@Composable
fun App(isDarkTheme: Boolean = false) {
    Surface {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            NavigationApp(isDarkTheme = isDarkTheme)
        }
    }
}
```

`NavigationApp` is the main entry point that:
- Initializes **Koin DI** with an isolated context
- Sets up **Jetpack Navigation** with screens
- Manages state via `NavigationViewModel`

### Key Types

```kotlin
// Navigation step — a single route segment
sealed class NavigationStep {
    data class ByFlor(...)            // Movement within a floor
    data class TransitionToFlor(...)   // Transition between floors
    data class TransitionToBuilding(...)// Transition between buildings
    data class TransitionToOutDoor(...)// Exit to outdoors
    data class OutDoorMaps(...)        // Outdoor route
    data class TransitionToInDoor(...) // Enter a building
}

// Complete route with metrics
data class NavigationDirection(
    val steps: List<NavigationStep>,
    val totalDistanceMeters: Double,
    val badge: String?
) {
    val estimatedTimeMinutes: Double   // Time estimate (80m/min)
}
```

---

## ⚙️ Configuration

The library requires API keys to function. Create a `local.properties` file in the project root:

```properties
GOOGLE_MAPS_API_KEY=your_google_maps_key
MAPBOX_API_KEY=your_mapbox_key
API_KEY=your_api_key
BASE_URL=https://your-api-url.com
```

Or set them via environment variables:

```bash
export GOOGLE_MAPS_API_KEY=your_google_maps_key
export MAPBOX_API_KEY=your_mapbox_key
export API_KEY=your_api_key
export BASE_URL=https://your-api-url.com
```

---

## 🏃 Running the Sample App

The project includes a fully functional demo application:

```bash
# 🖥️ Desktop (JVM)
./gradlew :sample:composeApp:run

# 🌐 Web (WASM) — opens in the browser
./gradlew :sample:composeApp:wasmJsBrowserRun

# 🤖 Android — open the project in Android Studio and run the sample app

# 🍎 iOS — open in Xcode:
open sample/iosApp/iosApp.xcodeproj
```

---

## 📤 Publishing

### Publish to MavenLocal

```bash
./gradlew :JetNavigation:publishToMavenLocal
```

Artifacts will be available at `~/.m2/repository/com/oriooneee/jet/navigation/`

### Publish to MavenCentral

<details>
<summary>📋 Step-by-step instructions</summary>

1. **Create an account** on [Sonatype Central Portal](https://central.sonatype.org/register/central-portal/#create-an-account)

2. **Fill in the developer details** in `./JetNavigation/build.gradle.kts` (id, name, email, project url)

3. **Generate a GPG key:**
   ```bash
   gpg --full-gen-key
   gpg --keyserver keyserver.ubuntu.com --send-keys XXXXXXXX
   gpg --export-secret-key XXXXXXXX > XXXXXXXX.gpg
   ```

4. **Add the configuration** to `gradle.properties`:
   ```properties
   signing.keyId=XXXXXXXX
   signing.password=your_key_password
   signing.secretKeyRingFile=../XXXXXXXX.gpg
   mavenCentralUsername=generated_username
   mavenCentralPassword=generated_password
   ```

5. **Publish:**
   ```bash
   ./gradlew :JetNavigation:publishAndReleaseToMavenCentral --no-configuration-cache
   ```

</details>

---

## 🛠️ Tech Stack

| Technology | Purpose |
|---|---|
| [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) | Shared code across all platforms |
| [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) | Declarative UI |
| [Ktor Client](https://ktor.io/docs/client-overview.html) | HTTP client (OkHttp / Darwin / WASM) |
| [Koin](https://insert-koin.io/) | Dependency Injection |
| [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) | JSON serialization |
| [Kotlinx Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Asynchronous programming |
| [Google Maps Compose](https://developers.google.com/maps/documentation/android-sdk/maps-compose) | Maps for Android |
| [MapBox](https://www.mapbox.com/) | Maps for cross-platform scenarios |
| [Coil](https://coil-kt.github.io/coil/) | Image loading |
| [Jetpack Navigation](https://developer.android.com/jetpack/compose/navigation) | Screen navigation |

---

## 📄 License

```
MIT License

Copyright (c) 2025 oriooneee

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
```

---

<div align="center">

**Made with ❤️ using Kotlin Multiplatform and Compose**

</div>
