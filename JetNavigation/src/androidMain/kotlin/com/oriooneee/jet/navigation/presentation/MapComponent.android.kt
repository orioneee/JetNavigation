package com.oriooneee.jet.navigation.presentation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.rememberMapState
import com.mapbox.maps.extension.compose.style.projection.generated.Projection
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.extension.compose.style.standard.ThemeValue
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.plugin.gestures.generated.GesturesSettings
import com.mapbox.maps.plugin.viewport.data.DefaultViewportTransitionOptions
import com.mapbox.maps.plugin.viewport.data.OverviewViewportStateOptions
import com.oriooneee.jet.navigation.buildconfig.BuildConfig
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@Composable
actual fun MapComponent(
    modifier: Modifier,
    step: NavigationStep.OutDoorMaps,
    isDarkTheme: Boolean
) {
    MapboxOptions.accessToken = BuildConfig.MAPS_API_KEY

    val routePoints = remember(step) {
        step.path.map { Point.fromLngLat(it.longitude, it.latitude) }
    }

    val mapState = rememberMapState {
        gesturesSettings = GesturesSettings {
            rotateEnabled = false
            pitchEnabled = false
        }
    }
    val viewportState = rememberMapViewportState()
    val googleBlue = Color(0xFF4285F4)
    val routeBorderColor = Color(0xFF1558B0)
    val whiteColor = Color.White

    MapboxMap(
        modifier = modifier,
        mapState = mapState,
        mapViewportState = viewportState,
        compass = {},
        scaleBar = {},
        attribution = {},
        logo = {},
        style = {
            MapboxStandardStyle(
                init = {
                    theme = ThemeValue.MONOCHROME
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

                        CircleAnnotation(point = routePoints.first()) {
                            circleRadius = 4.0
                            circleStrokeWidth = 2.0
                            circleColor = googleBlue
                            circleStrokeColor = whiteColor
                        }

                        CircleAnnotation(point = routePoints.last()) {
                            circleRadius = 4.0
                            circleStrokeWidth = 2.0
                            circleColor = whiteColor
                            circleStrokeColor = googleBlue
                        }
                    }
                },
                projection = Projection.MERCATOR,
            )
        }
    ){
        MapEffect {
            mapState.mapLoadedEvents.first()
            val geometry = LineString.fromLngLats(routePoints)
            val overviewOptions = OverviewViewportStateOptions.Builder()
                .geometry(geometry)
                .bearing(18.5)
                .padding(EdgeInsets(100.0, 100.0, 100.0, 100.0))
                .build()

            viewportState.transitionToOverviewState(
                overviewViewportStateOptions = overviewOptions,
                defaultTransitionOptions = DefaultViewportTransitionOptions.Builder()
                    .maxDurationMs(0)
                    .build()
            )
        }
    }
}