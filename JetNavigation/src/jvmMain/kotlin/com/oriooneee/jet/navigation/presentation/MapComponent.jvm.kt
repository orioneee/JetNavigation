package com.oriooneee.jet.navigation.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewStateWithHTMLData
import com.oriooneee.jet.navigation.buildconfig.BuildConfig
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


@Composable
actual fun MapComponent(
    modifier: Modifier,
    step: NavigationStep.OutDoorMaps?,
    isDarkTheme: Boolean
) {
    var isInitialized by remember { mutableStateOf(false) }
    var initializationError by remember { mutableStateOf<String?>(null) }

    val pathPointsJson = remember(step) {
        step?.path?.joinToString(prefix = "[", postfix = "]") { "[${it.longitude}, ${it.latitude}]" } ?: "[]"
    }

    val mapHtml = remember(pathPointsJson, isDarkTheme) {
        getMapboxHtml(BuildConfig.MAPS_API_KEY, pathPointsJson, isDarkTheme)
    }

    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                KCEF.init(
                    builder = {
                        installDir(File("kcef-bundle"))
                        settings { cachePath = File("cache").absolutePath }
                    },
                    onError = { initializationError = it?.localizedMessage }
                )
            }
            isInitialized = true
        } catch (e: Exception) {
            initializationError = e.localizedMessage
        }
    }

    if (initializationError != null) {
        Text("Error: $initializationError")
    } else if (!isInitialized) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Initializing Map...")
            Spacer(Modifier.weight(1f))
            CircularProgressIndicator()
        }
    } else {
        val state = rememberWebViewStateWithHTMLData(data = mapHtml)
        Column(modifier = modifier) {
            if (state.loadingState is LoadingState.Loading) {
                LinearProgressIndicator(
                    progress = (state.loadingState as LoadingState.Loading).progress,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            WebView(state = state, modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}