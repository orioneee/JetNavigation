package com.oriooneee.jet.navigation.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.oriooneee.jet.navigation.buildconfig.BuildConfig
import com.oriooneee.jet.navigation.domain.entities.Coordinates
import com.oriooneee.jet.navigation.domain.entities.NavigationStep

fun getMapboxHtml(
    accessToken: String,
    pathPoints: String,
    isDarkTheme: Boolean,
    isStatic: Boolean
): String {
    val markerSvgText = """
        <svg height="50" id="vector" viewBox="0 0 32 50" width="32" xmlns="http://www.w3.org/2000/svg">
            <defs>
                <linearGradient gradientUnits="userSpaceOnUse" id="gradient_0" x1="-29.352" x2="82.774"
                    y1="-5.248" y2="-3.662">
                    <stop offset="0" stop-color="#6EA8FF" />
                    <stop offset="0.5" stop-color="#4285F4" />
                    <stop offset="1" stop-color="#2A6DE0" />
                </linearGradient>
            </defs>
            <path d="M16,50.001C18.104,50.001 19.809,49.153 19.809,48.107C19.809,47.061 18.104,46.213 16,46.213C13.896,46.213 12.19,47.061 12.19,48.107C12.19,49.153 13.896,50.001 16,50.001Z"
                fill="#000000"
                fill-opacity="0.3" id="path_0" stroke-opacity="0.3" />
            <path d="M16,0C24.837,0 32,7.123 32,15.909C32,24.184 25.646,30.984 17.524,31.747V46.969C17.524,47.806 16.842,48.485 16,48.485C15.158,48.485 14.476,47.806 14.476,46.969V31.747C6.354,30.984 0,24.184 0,15.909C0,7.123 7.163,0 16,0Z"
                fill="#ffffff"
                fill-rule="evenodd" id="path_1" />
            <path d="M28.952,15.91C28.952,8.797 23.153,3.031 16,3.031C8.847,3.031 3.048,8.797 3.048,15.91C3.048,23.023 8.847,28.789 16,28.789C23.153,28.789 28.952,23.023 28.952,15.91Z"
                fill="url(#gradient_0)"
                id="path_2" />
            <path d="M21.334,15.91C21.334,12.982 18.946,10.607 16,10.607C13.055,10.607 10.667,12.982 10.667,15.91C10.667,18.839 13.055,21.213 16,21.213C18.946,21.213 21.334,18.839 21.334,15.91Z"
                fill="#ffffff"
                id="path_3" />
        </svg>
    """.trimIndent()
    val googleBlue = "#4285F4"
    val routeBorderColor = "#1558B0"
    val whiteColor = "#FFFFFF"

    val lightPreset = if (isDarkTheme) "night" else "day"
    val interactionEnabled = if (isStatic) "false" else "true"

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="initial-scale=1,maximum-scale=1,user-scalable=no">
            <link href="https://api.mapbox.com/mapbox-gl-js/v3.9.4/mapbox-gl.css" rel="stylesheet">
            <script src="https://api.mapbox.com/mapbox-gl-js/v3.9.4/mapbox-gl.js"></script>
            <style>
                body { margin: 0; padding: 0; }
                #map { position: absolute; top: 0; bottom: 0; width: 100%; }
                .marker-start { background-color: $googleBlue; border: 2px solid $whiteColor; width: 14px; height: 14px; border-radius: 50%; }
                .marker-end { background: none; border: none; display: flex; justify-content: center; align-items: center; }
                .mapboxgl-ctrl-logo { display: none !important; }
                .mapboxgl-ctrl-attrib { display: none !important; }
                .mapboxgl-ctrl-bottom-left, .mapboxgl-ctrl-bottom-right { display: none !important; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                mapboxgl.accessToken = '$accessToken';
                const coordinates = $pathPoints;
                const center = coordinates.length > 0 ? coordinates[0] : [0, 0];

                const map = new mapboxgl.Map({
                    container: 'map',
                    style: 'mapbox://styles/mapbox/standard',
                    center: center,
                    zoom: 15,
                    bearing: 18.5,
                    pitch: 0,
                    dragRotate: false,
                    scrollZoom: $interactionEnabled,
                    dragPan: $interactionEnabled,
                    doubleClickZoom: $interactionEnabled,
                    touchZoomRotate: $interactionEnabled,
                    boxZoom: $interactionEnabled,
                    attributionControl: false,
                    config: {
                        basemap: {
                            lightPreset: '$lightPreset',
                            theme: 'faded'
                        }
                    }
                });

                map.on('style.load', () => {
                    try {
                        map.setConfigProperty('basemap', 'lightPreset', '$lightPreset');
                        map.setConfigProperty('basemap', 'theme', 'faded');
                    } catch (e) {
                    }

                    if (coordinates.length >= 2) {
                        map.addSource('route', {
                            'type': 'geojson',
                            'data': {
                                'type': 'Feature',
                                'geometry': { 'type': 'LineString', 'coordinates': coordinates }
                            }
                        });
                        
                        map.addLayer({
                            'id': 'route-outline',
                            'type': 'line',
                            'source': 'route',
                            'slot': 'middle', 
                            'layout': { 'line-join': 'round', 'line-cap': 'round' },
                            'paint': {
                                'line-color': '$routeBorderColor',
                                'line-width': 8,
                                'line-emissive-strength': 1
                            }
                        });

                        map.addLayer({
                            'id': 'route-main',
                            'type': 'line',
                            'source': 'route',
                            'slot': 'middle',
                            'layout': { 'line-join': 'round', 'line-cap': 'round' },
                            'paint': {
                                'line-color': '$googleBlue',
                                'line-width': 8,
                                'line-emissive-strength': 1
                            }
                        });

                        new mapboxgl.Marker(Object.assign(document.createElement('div'), {className:'marker-start'}))
                            .setLngLat(coordinates[0]).addTo(map);

                        const endEl = document.createElement('div');
                        endEl.className = 'marker-end';
                        endEl.innerHTML = `$markerSvgText`;

                        new mapboxgl.Marker({ element: endEl, anchor: 'bottom' })
                            .setLngLat(coordinates[coordinates.length - 1]).addTo(map);

                        const bounds = new mapboxgl.LngLatBounds();
                        coordinates.forEach(coord => bounds.extend(coord));
                        map.fitBounds(bounds, { padding: 25, bearing: 18.5, duration: 0 });
                    }
                });
            </script>
        </body>
        </html>
    """.trimIndent()
}

private fun encodeDelta(result: StringBuilder, value: Int) {
    var num = if (value < 0) {
        (value shl 1).inv()
    } else {
        value shl 1
    }

    while (num >= 0x20) {
        result.append(((0x20 or (num and 0x1f)) + 63).toChar())
        num = num shr 5
    }
    result.append((num + 63).toChar())
}

fun encodePolyline(coordinates: List<Coordinates>): String {
    var lastLat = 0
    var lastLng = 0
    val result = StringBuilder()

    for (coord in coordinates) {
        val lat = (coord.latitude * 1e5).toInt()
        val lng = (coord.longitude * 1e5).toInt()

        encodeDelta(result, lat - lastLat)
        encodeDelta(result, lng - lastLng)

        lastLat = lat
        lastLng = lng
    }

    return result.toString()
}

fun getStaticMapUrl(
    step: NavigationStep.OutDoorMaps,
    accessToken: String,
    isDarkTheme: Boolean
): String {
    if (step.path.isEmpty()) return ""

    val styleId = if (isDarkTheme) "mapbox/dark-v11" else "mapbox/streets-v12"
    val colorHex = "4285F4"
    val width = 500
    val height = 500

    val rawPolyline = encodePolyline(step.path)
    val encodedPath = rawPolyline.escapeUrlParam()

    val pathOverlay = "path-5+$colorHex-1($encodedPath)"

    val startPoint = step.path.first()
    val endPoint = step.path.last()

    val startPin = "pin-s+$colorHex(${startPoint.longitude},${startPoint.latitude})"

    val markerImageUrl = "https://raw.githubusercontent.com/orioneee/JetNavigation/refs/heads/main/JetNavigation/src/commonMain/composeResources/files/marker.png"
    val encodedMarkerUrl = markerImageUrl.escapeUrlParam()

    val endPin = "url-$encodedMarkerUrl(${endPoint.longitude},${endPoint.latitude})"

    val overlays = "$pathOverlay,$endPin,$startPin"
    val bounds = "[28.40455,49.22823,28.41498,49.2358]"

    return "https://api.mapbox.com/styles/v1/$styleId/static/$overlays/$bounds/${width}x${height}@2x?access_token=$accessToken&attribution=false&logo=false"
}

private fun String.escapeUrlParam(): String {
    val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    return buildString {
        this@escapeUrlParam.forEach { char ->
            if (char in unreserved) {
                append(char)
            } else {
                append("%${char.code.toString(16).uppercase().padStart(2, '0')}")
            }
        }
    }
}

@Composable
fun StaticImageMap(
    modifier: Modifier = Modifier,
    step: NavigationStep.OutDoorMaps?,
    isDarkTheme: Boolean
) {
    val imageUrl = remember(step, isDarkTheme) {
        step?.let {
            getStaticMapUrl(
                step = it,
                accessToken = BuildConfig.MAPBOX_API_KEY,
                isDarkTheme = isDarkTheme
            )
        }
    }

    if (imageUrl != null) {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = "Static Map",
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            error = {
                println("Failed to load static map image url: $imageUrl error: ${it.result.throwable}")
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Unavailable",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = ProgressIndicatorDefaults.circularColor,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        strokeCap = StrokeCap.Round,
                    )
                }
            }
        )
    } else {
        MapPlaceholderContent(step)
    }
}

@Composable
expect fun MapComponent(
    modifier: Modifier = Modifier,
    step: NavigationStep.OutDoorMaps?,
    isDarkTheme: Boolean,
    isStatic: Boolean = false
)

@Composable
fun MapPlaceholderContent(
    step: NavigationStep.OutDoorMaps?,
) {
    step ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF81C784).copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Outlined.Park,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color(0xFF4CAF50)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Outdoor Navigation",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF2E7D32)
            )

            Spacer(Modifier.height(24.dp))

            if (step.fromDescription != null || step.toDescription != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    step.fromDescription?.let { from ->
                        Text(
                            text = from,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (step.fromDescription != null && step.toDescription != null) {
                        Text(
                            text = "\u2193",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF4CAF50)
                        )
                    }

                    step.toDescription?.let { to ->
                        Text(
                            text = to,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            } else {
                Text(
                    "${step.path.size} waypoints",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "iOS: Provide LocalNativeFactory to display the map",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}