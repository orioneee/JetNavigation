package com.oriooneee.jet.navigation.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.WebElementView
import com.oriooneee.jet.navigation.buildconfig.BuildConfig
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import kotlinx.browser.document
import org.w3c.dom.HTMLIFrameElement

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun MapComponent(
    modifier: Modifier,
    step: NavigationStep.OutDoorMaps?,
    isDarkTheme: Boolean,
    isStatic: Boolean
) {
    if(isStatic){
        StaticImageMap(
            modifier = modifier,
            step = step,
            isDarkTheme = isDarkTheme
        )
    } else{
        val pathPointsJson = remember(step) {
            step?.path?.joinToString(prefix = "[", postfix = "]") { "[${it.longitude}, ${it.latitude}]" }
                ?: "[]"
        }

        val mapHtml = remember(pathPointsJson, isDarkTheme) {
            getMapboxHtml(pathPointsJson, isDarkTheme, isStatic)
        }

        Box(modifier = modifier) {
            WebElementView(
                factory = {
                    (document.createElement("iframe") as HTMLIFrameElement).apply {
                        style.width = "100%"
                        style.height = "100%"
                        style.border = "none"
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { iframe ->
                    iframe.srcdoc = mapHtml
                }
            )
        }
    }
}

internal actual val BuildConfig.MAPBOX_TOKEN: String
    get() = BuildConfig.MAPBOX_API_KEY_WEB