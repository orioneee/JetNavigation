package com.oriooneee.jet.navigation.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewStateWithHTMLData
import com.oriooneee.jet.navigation.buildconfig.BuildConfig
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
var isInitialized by mutableStateOf(false)

@Composable
actual fun MapComponent(
    modifier: Modifier,
    step: NavigationStep.OutDoorMaps?,
    isDarkTheme: Boolean
) {
    var loadingStatus by remember { mutableStateOf("Initializing...") }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var initializationError by remember { mutableStateOf<String?>(null) }
    var restartRequired by remember { mutableStateOf(false) }

    val pathPointsJson = remember(step) {
        step?.path?.joinToString(prefix = "[", postfix = "]") { "[${it.longitude}, ${it.latitude}]" } ?: "[]"
    }

    val mapHtml = remember(pathPointsJson, isDarkTheme) {
        getMapboxHtml(BuildConfig.MAPS_API_KEY, pathPointsJson, isDarkTheme)
    }

    LaunchedEffect(Unit) {
        if (!isInitialized) {
            withContext(Dispatchers.IO) {
                try {
                    KCEF.init(
                        builder = {
                            installDir(File("kcef-bundle"))
                            progress {
                                onLocating {
                                    loadingStatus = "Locating existing installation..."
                                }
                                onDownloading {
                                    loadingStatus = "Downloading Map Components..."
                                    downloadProgress = max(it, 0f)
                                }
                                onExtracting {
                                    loadingStatus = "Extracting packages..."
                                }
                                onInstall {
                                    loadingStatus = "Installing..."
                                }
                                onInitializing {
                                    loadingStatus = "Initializing Engine..."
                                }
                                onInitialized {
                                    loadingStatus = "Ready"
                                    isInitialized = true
                                }
                            }
                            settings {
                                cachePath = File("cache").absolutePath
                            }
                        },
                        onError = {
                            it?.printStackTrace()
                            initializationError = it?.localizedMessage ?: "Unknown KCEF Error"
                        },
                        onRestartRequired = {
                            restartRequired = true
                        }
                    )
                } catch (e: Exception) {
                    initializationError = e.localizedMessage
                }
            }
        }
    }

    Column(modifier = modifier) {
        when {
            restartRequired -> {
                ErrorView("Restart application to complete installation.")
            }
            initializationError != null -> {
                ErrorView("Map initialization error: $initializationError")
            }
            !isInitialized -> {
                DownloadingLoader(status = loadingStatus, progress = downloadProgress)
            }
            else -> {
                val state = rememberWebViewStateWithHTMLData(data = mapHtml)

                if (state.loadingState is LoadingState.Loading) {
                    LinearProgressIndicator(
                        progress = (state.loadingState as LoadingState.Loading).progress,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                WebView(
                    state = state,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun DownloadingLoader(status: String, progress: Float) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Preparing Map",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (status.contains("Downloading", ignoreCase = true)) {
            LinearProgressIndicator(
                progress = progress / 100f,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${progress.toInt()}%",
                style = MaterialTheme.typography.labelMedium
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Please wait, this happens once.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorView(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
}