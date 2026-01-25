package com.oriooneee.jet.navigation.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewStateWithHTMLData
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

    val mapboxAccessToken = "YOUR_MAPBOX_ACCESS_TOKEN_HERE"

    val coordinatesJson = remember(step) {
        step?.path?.joinToString(prefix = "[", postfix = "]") {
            "[${it.longitude}, ${it.latitude}]"
        } ?: "[]"
    }

    val mapStyle = if (isDarkTheme) "mapbox://styles/mapbox/dark-v11" else "mapbox://styles/mapbox/streets-v12"
    val googleBlue = "#4285F4"
    val whiteColor = "#FFFFFF"

    val mapboxHtml = """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <title>Mapbox</title>
        <meta name="viewport" content="initial-scale=1,maximum-scale=1,user-scalable=no">
        <link href="https://api.mapbox.com/mapbox-gl-js/v3.1.2/mapbox-gl.css" rel="stylesheet">
        <script src="https://api.mapbox.com/mapbox-gl-js/v3.1.2/mapbox-gl.js"></script>
        <style>
            body { margin: 0; padding: 0; }
            #map { position: absolute; top: 0; bottom: 0; width: 100%; }
            .marker-start {
                background-color: $googleBlue;
                border: 2px solid $whiteColor;
                width: 12px;
                height: 12px;
                border-radius: 50%;
                box-shadow: 0 0 2px rgba(0,0,0,0.3);
            }
            .marker-end {
                background-color: $whiteColor;
                border: 2px solid $googleBlue;
                width: 12px;
                height: 12px;
                border-radius: 50%;
                box-shadow: 0 0 2px rgba(0,0,0,0.3);
            }
        </style>
        </head>
        <body>
        <div id="map"></div>
        <script>
            mapboxgl.accessToken = '$mapboxAccessToken';
            
            const coordinates = $coordinatesJson;
            
            // Если координат нет, ставим дефолт (Москва)
            const center = coordinates.length > 0 ? coordinates[0] : [37.6173, 55.7558];

            const map = new mapboxgl.Map({
                container: 'map',
                style: '$mapStyle',
                center: center,
                zoom: 15,
                pitch: 0,
                bearing: 0,
                dragRotate: false, 
                touchPitch: false
            });

            map.on('load', () => {
                if (coordinates.length >= 2) {
                    
                    map.addSource('route', {
                        'type': 'geojson',
                        'data': {
                            'type': 'Feature',
                            'properties': {},
                            'geometry': {
                                'type': 'LineString',
                                'coordinates': coordinates
                            }
                        }
                    });

                    map.addLayer({
                        'id': 'route',
                        'type': 'line',
                        'source': 'route',
                        'layout': {
                            'line-join': 'round',
                            'line-cap': 'round'
                        },
                        'paint': {
                            'line-color': '$googleBlue',
                            'line-width': 8
                        }
                    });

                    // Start Marker (Blue with White border)
                    const elStart = document.createElement('div');
                    elStart.className = 'marker-start';
                    new mapboxgl.Marker(elStart)
                        .setLngLat(coordinates[0])
                        .addTo(map);

                    // End Marker (White with Blue border)
                    const elEnd = document.createElement('div');
                    elEnd.className = 'marker-end';
                    new mapboxgl.Marker(elEnd)
                        .setLngLat(coordinates[coordinates.length - 1])
                        .addTo(map);

                    // Fit Bounds with padding
                    const bounds = new mapboxgl.LngLatBounds();
                    coordinates.forEach(coord => bounds.extend(coord));
                    map.fitBounds(bounds, {
                        padding: 100
                    });
                }
            });
        </script>
        </body>
        </html>
    """.trimIndent()

    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                KCEF.init(
                    builder = {
                        installDir(File("kcef-bundle"))
                        settings {
                            cachePath = File("cache").absolutePath
                        }
                    },
                    onError = {
                        it?.printStackTrace()
                        initializationError = it?.localizedMessage
                    }
                )
            }
            withContext(Dispatchers.Main) {
                isInitialized = true
            }
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
            Text("Initializing Map Engine...")
            Spacer(Modifier.weight(1f))
            CircularProgressIndicator()
        }
    } else {
        Column(modifier = modifier) {
            val state = rememberWebViewStateWithHTMLData(
                data = mapboxHtml
            )

            val loadingState = state.loadingState
            if (loadingState is LoadingState.Loading) {
                LinearProgressIndicator(
                    progress = loadingState.progress,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            WebView(
                state = state,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}