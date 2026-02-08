package com.oriooneee.jet.navigation.data

import com.oriooneee.jet.navigation.buildconfig.BuildConfig

internal actual val BuildConfig.API_KEY: String
    get() = BuildConfig.API_KEY_DESKTOP