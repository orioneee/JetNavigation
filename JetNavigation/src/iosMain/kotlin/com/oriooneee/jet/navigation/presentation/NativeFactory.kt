package com.oriooneee.jet.navigation.presentation

import androidx.compose.runtime.staticCompositionLocalOf
import com.oriooneee.jet.navigation.buildconfig.BuildConfig
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import platform.UIKit.UIView

val LocalNativeFactory = staticCompositionLocalOf<NativeFactory?> {
    null
}


interface NativeFactory {
    fun getMapBoxMap(
        step: NavigationStep.OutDoorMaps?,
        isDarkTheme: Boolean,
    ): UIView

    companion object {
        fun getMapBoxToken() = BuildConfig.MAPBOX_TOKEN
    }
}