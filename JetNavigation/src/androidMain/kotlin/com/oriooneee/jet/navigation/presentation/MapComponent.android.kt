package com.oriooneee.jet.navigation.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.extension.compose.ComposeMapInitOptions
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationGroup
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.rememberMapState
import com.mapbox.maps.extension.compose.style.projection.generated.Projection
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.extension.compose.style.standard.ThemeValue
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.gestures.generated.GesturesSettings
import com.oriooneee.jet.navigation.buildconfig.BuildConfig
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
actual fun MapComponent(
    modifier: Modifier,
    step: NavigationStep.OutDoorMaps?,
    isDarkTheme: Boolean
) {
    MapboxOptions.accessToken = BuildConfig.MAPS_API_KEY

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
                    .withCircleStrokeColor(whiteColor.toArgb())
                    ,
                CircleAnnotationOptions()
                    .withPoint(routePoints.last())
                    .withCircleRadius(4.0)
                    .withCircleStrokeWidth(2.0)
                    .withCircleColor(whiteColor.toArgb())
                    .withCircleStrokeColor(googleBlue.toArgb())
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
                        PolylineAnnotation(points = routePoints) {
                            lineWidth = 8.0
                            lineJoin = LineJoin.ROUND
                            lineColor = routeBorderColor
                            lineEmissiveStrength = 1.0
                        }

                        PolylineAnnotation(points = routePoints) {
                            lineWidth = 8.0
                            lineJoin = LineJoin.ROUND
                            lineColor = googleBlue
                            lineEmissiveStrength = 1.0
                        }

                        CircleAnnotationGroup(
                            annotations = circleAnnotations
                        ){
                            circleEmissiveStrength = 1.0
                        }
                    }
                },
                projection = Projection.MERCATOR,
            )
        }
    ) {
        MapEffect {
            mapboxMapFlow.value = it.mapboxMap
        }
    }
}