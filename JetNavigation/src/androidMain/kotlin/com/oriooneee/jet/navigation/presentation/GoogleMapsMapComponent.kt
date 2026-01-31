package com.oriooneee.jet.navigation.presentation
//
//import android.graphics.Bitmap
//import android.graphics.Canvas
//import android.graphics.Paint
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.remember
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.toArgb
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.platform.LocalDensity
//import androidx.compose.ui.unit.dp
//import androidx.core.content.ContextCompat
//import androidx.core.graphics.drawable.toBitmap
//import com.google.android.gms.maps.CameraUpdateFactory
//import com.google.android.gms.maps.model.BitmapDescriptorFactory
//import com.google.android.gms.maps.model.CameraPosition
//import com.google.android.gms.maps.model.LatLng
//import com.google.android.gms.maps.model.LatLngBounds
//import com.google.android.gms.maps.model.MapStyleOptions
//import com.google.maps.android.compose.GoogleMap
//import com.google.maps.android.compose.MapEffect
//import com.google.maps.android.compose.MapProperties
//import com.google.maps.android.compose.MapUiSettings
//import com.google.maps.android.compose.MapsComposeExperimentalApi
//import com.google.maps.android.compose.Marker
//import com.google.maps.android.compose.Polyline
//import com.google.maps.android.compose.rememberCameraPositionState
//import com.google.maps.android.compose.rememberMarkerState
//import com.oriooneee.jet.navigation.R
//import com.oriooneee.jet.navigation.domain.entities.NavigationStep
//
//@OptIn(MapsComposeExperimentalApi::class)
//@Composable
//fun GoogleMapsMapComponent(
//    modifier: Modifier,
//    step: NavigationStep.OutDoorMaps?,
//    isDarkTheme: Boolean
//) {
//    val context = LocalContext.current
//    val density = LocalDensity.current
//
//    val endMarkerDrawable = remember {
//        ContextCompat.getDrawable(context, R.drawable.ic_map_marker)?.toBitmap()
//    }
//
//    val googleBlue = Color(0xFF4285F4)
//    val routeBorderColor = Color(0xFF1558B0)
//
//    val startCircleBitmap = remember(density) {
//        val radiusPx = with(density) { 6.dp.toPx() }
//        val strokePx = with(density) { 4.dp.toPx() }
//        val size = ((radiusPx + strokePx) * 2).toInt()
//        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
//        val canvas = Canvas(bitmap)
//        val center = size / 2f
//        canvas.drawCircle(center, center, radiusPx + strokePx / 2, Paint().apply {
//            color = android.graphics.Color.WHITE
//            style = Paint.Style.FILL
//            isAntiAlias = true
//        })
//        canvas.drawCircle(center, center, radiusPx, Paint().apply {
//            color = googleBlue.toArgb()
//            style = Paint.Style.FILL
//            isAntiAlias = true
//        })
//        bitmap
//    }
//
//    val routePoints = remember(step) {
//        step?.path?.map { LatLng(it.latitude, it.longitude) } ?: emptyList()
//    }
//
//    val cameraPositionState = rememberCameraPositionState()
//
//    val uiSettings = remember {
//        MapUiSettings(
//            rotationGesturesEnabled = false,
//            tiltGesturesEnabled = false,
//            compassEnabled = false,
//            mapToolbarEnabled = false,
//            zoomControlsEnabled = false,
//            myLocationButtonEnabled = false,
//        )
//    }
//
//    val mapProperties = remember(isDarkTheme) {
//        if (isDarkTheme) {
//            MapProperties(
//                mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.google_maps_dark_style)
//            )
//        } else {
//            MapProperties()
//        }
//    }
//
//    GoogleMap(
//        modifier = modifier,
//        cameraPositionState = cameraPositionState,
//        uiSettings = uiSettings,
//        properties = mapProperties,
//    ) {
//        MapEffect(routePoints) { map ->
//            if (routePoints.size >= 2) {
//                val bounds = LatLngBounds.builder().apply {
//                    routePoints.forEach { include(it) }
//                }.build()
//                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
//                val pos = map.cameraPosition
//                map.moveCamera(
//                    CameraUpdateFactory.newCameraPosition(
//                        CameraPosition.Builder()
//                            .target(pos.target)
//                            .zoom(pos.zoom)
//                            .bearing(18.5f)
//                            .build()
//                    )
//                )
//            }
//        }
//
//        if (routePoints.size >= 2) {
//            Polyline(
//                points = routePoints,
//                color = routeBorderColor,
//                width = 24f
//            )
//
//            Polyline(
//                points = routePoints,
//                color = googleBlue,
//                width = 20f
//            )
//
//            // Start circle marker (fixed pixel size)
//            val startIcon = remember(startCircleBitmap) {
//                BitmapDescriptorFactory.fromBitmap(startCircleBitmap)
//            }
//            Marker(
//                state = rememberMarkerState(position = routePoints.first()),
//                icon = startIcon,
//                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
//                flat = true
//            )
//
//            // End marker
//            endMarkerDrawable?.let { bitmap ->
//                val icon = remember(bitmap) { BitmapDescriptorFactory.fromBitmap(bitmap) }
//                Marker(
//                    state = rememberMarkerState(position = routePoints.last()),
//                    icon = icon,
//                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 1.0f)
//                )
//            }
//        }
//    }
//}
