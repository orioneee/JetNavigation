package com.oriooneee.jet.navigation.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.extension.compose.ComposeMapInitOptions
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.IconImage
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationGroup
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.rememberMapState
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.extension.compose.style.standard.ThemeValue
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.gestures.generated.GesturesSettings
import com.oriooneee.jet.navigation.R
import com.oriooneee.jet.navigation.buildconfig.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun MapBoxMapComponent(
    modifier: androidx.compose.ui.Modifier,
    step: com.oriooneee.jet.navigation.domain.entities.NavigationStep.OutDoorMaps?,
    isDarkTheme: Boolean
) {
    MapboxOptions.accessToken = BuildConfig.MAPBOX_API_KEY

    val context = LocalContext.current
    val endMarkerBitmap = remember {
        ContextCompat.getDrawable(context, R.drawable.ic_map_marker)?.toBitmap()
    }

    val routePoints = remember(step) {
        step?.path?.map { Point.fromLngLat(it.longitude, it.latitude) } ?: emptyList()
    }

    val mapState = rememberMapState {
        gesturesSettings = GesturesSettings {
            rotateEnabled = false
            pitchEnabled = false
        }
    }
    val mapboxMapFlow = remember {
        MutableStateFlow<MapboxMap?>(null)
    }
    val scope = rememberCoroutineScope()
    val viewportState = rememberMapViewportState {
        scope.launch {
            val mapboxMap = mapboxMapFlow.filterNotNull().first()
            if (routePoints.size >= 2) {
                val geometry = LineString.fromLngLats(routePoints)
                setCameraOptions(
                    mapboxMap.cameraForGeometry(
                        geometry = geometry,
                        bearing = 18.5,
                        geometryPadding = EdgeInsets(100.0, 100.0, 100.0, 100.0)
                    )
                )
            }
        }
    }
    val googleBlue = Color(0xFF4285F4)
    val routeBorderColor = Color(0xFF1558B0)
    val whiteColor = Color.White

    val circleAnnotations = remember(routePoints) {
        if (routePoints.size >= 2) {
            listOf(
                CircleAnnotationOptions()
                    .withPoint(routePoints.first())
                    .withCircleRadius(4.0)
                    .withCircleStrokeWidth(2.0)
                    .withCircleColor(googleBlue.toArgb())
                    .withCircleStrokeColor(whiteColor.toArgb()),
            )
        } else {
            emptyList()
        }
    }
    MapboxMap(
        modifier = modifier,
        mapState = mapState,
        mapViewportState = viewportState,
        compass = {},
        scaleBar = {},
        attribution = {},
        logo = {},
        composeMapInitOptions = with(LocalDensity.current) {
            ComposeMapInitOptions(density, textureView = true)
        },
        style = {
            MapboxStandardStyle(
                init = {
                    theme = ThemeValue.FADED
                    lightPreset = if (isDarkTheme) {
                        LightPresetValue.NIGHT
                    } else {
                        LightPresetValue.DAY
                    }
                },
                topSlot = {
                    if (routePoints.size >= 2) {
                        // Граница маршрута
                        PolylineAnnotation(points = routePoints) {
                            lineWidth = 8.0
                            lineJoin = LineJoin.ROUND
                            lineColor = routeBorderColor
                            lineEmissiveStrength = 1.0
                        }

                        // Основная линия маршрута
                        PolylineAnnotation(points = routePoints) {
                            lineWidth = 8.0
                            lineJoin = LineJoin.ROUND
                            lineColor = googleBlue
                            lineEmissiveStrength = 1.0
                        }

                        CircleAnnotationGroup(
                            annotations = circleAnnotations
                        ) {
                            circleEmissiveStrength = 1.0
                        }

                        endMarkerBitmap?.let { bitmap ->
                            PointAnnotation(point = routePoints.last()) {
                                iconImage = IconImage(bitmap)
                                iconAnchor =
                                    com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor.BOTTOM
                            }
                        }
                    }
                },
            )
        }
    ) {
        MapEffect {
            mapboxMapFlow.value = it.mapboxMap
        }
    }
}