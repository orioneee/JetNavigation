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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
fun getMapboxHtml(
    accessToken: String,
    pathPoints: String,
    isDarkTheme: Boolean
): String {
    // Выбираем стиль в зависимости от темы приложения
    val styleUrl = if (isDarkTheme) "mapbox://styles/mapbox/dark-v11" else "mapbox://styles/mapbox/streets-v12"

    val googleBlue = "#4285F4"
    val routeBorderColor = "#1558B0"
    val whiteColor = "#FFFFFF"

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="initial-scale=1,maximum-scale=1,user-scalable=no">
            <link href="https://api.mapbox.com/mapbox-gl-js/v3.1.2/mapbox-gl.css" rel="stylesheet">
            <script src="https://api.mapbox.com/mapbox-gl-js/v3.1.2/mapbox-gl.js"></script>
            <style>
                body { margin: 0; padding: 0; }
                #map { position: absolute; top: 0; bottom: 0; width: 100%; }
                .marker-start { background-color: $googleBlue; border: 2px solid $whiteColor; width: 14px; height: 14px; border-radius: 50%; }
                .marker-end { background-color: $whiteColor; border: 2px solid $googleBlue; width: 14px; height: 14px; border-radius: 50%; }
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
                    style: '$styleUrl', // ИСПОЛЬЗУЕМ КЛАССИЧЕСКИЙ СТИЛЬ
                    center: center,
                    zoom: 15,
                    bearing: 18.5,
                    pitch: 0,
                    dragRotate: false
                });

                map.on('style.load', () => {
                    // УБРАЛИ setConfigProperty, так как они не нужны для streets-v12
                    
                    if (coordinates.length >= 2) {
                        map.addSource('route', {
                            'type': 'geojson',
                            'data': {
                                'type': 'Feature',
                                'geometry': { 'type': 'LineString', 'coordinates': coordinates }
                            }
                        });
                        
                        // ... (остальной код добавления слоев и маркеров без изменений) ...
                        
                        map.addLayer({
                            'id': 'route-outline',
                            'type': 'line',
                            'source': 'route',
                            'layout': { 'line-join': 'round', 'line-cap': 'round' },
                            'paint': {
                                'line-color': '$routeBorderColor',
                                'line-width': 10,
                                'line-emissive-strength': 1.0
                            }
                        });

                        map.addLayer({
                            'id': 'route-main',
                            'type': 'line',
                            'source': 'route',
                            'layout': { 'line-join': 'round', 'line-cap': 'round' },
                            'paint': {
                                'line-color': '$googleBlue',
                                'line-width': 8,
                                'line-emissive-strength': 1.0
                            }
                        });

                        new mapboxgl.Marker(Object.assign(document.createElement('div'), {className:'marker-start'}))
                            .setLngLat(coordinates[0]).addTo(map);

                        new mapboxgl.Marker(Object.assign(document.createElement('div'), {className:'marker-end'}))
                            .setLngLat(coordinates[coordinates.length - 1]).addTo(map);

                        const bounds = new mapboxgl.LngLatBounds();
                        coordinates.forEach(coord => bounds.extend(coord));
                        map.fitBounds(bounds, { padding: 100, bearing: 18.5, duration: 0 });
                    }
                });
            </script>
        </body>
        </html>
    """.trimIndent()
}
@Composable
expect fun MapComponent(
    modifier: Modifier = Modifier,
    step: NavigationStep.OutDoorMaps?,
    isDarkTheme: Boolean
)

@Composable
fun MapPlaceholderContent(
    step: NavigationStep.OutDoorMaps?
){
    step?: return
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
        }
    }
}