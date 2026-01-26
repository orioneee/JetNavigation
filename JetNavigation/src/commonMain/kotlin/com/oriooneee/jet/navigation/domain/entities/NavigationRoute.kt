package com.oriooneee.jet.navigation.domain.entities

import androidx.compose.ui.geometry.Offset
import com.oriooneee.jet.navigation.engine.models.FloorRenderData
import com.oriooneee.jet.navigation.engine.models.TextLabel

sealed class NavigationStep {
    data class ByFlor(
        val building: Int,
        val flor: Int,
        val image: FloorRenderData,
        val pointOfInterest: Offset = Offset.Zero,
        val routeBounds: Pair<Offset, Offset>? = null, // topLeft to bottomRight
        val textLabels: List<TextLabel> = emptyList()
    ) : NavigationStep() {
    }

    data class TransitionToFlor(
        val to: Int,
        val from: Int
    ) : NavigationStep()
    data class TransitionToBuilding(
        val form: Int,
        val to: Int
    ) : NavigationStep()
    data class TransitionToOutDoor(
        val fromBuilding: Int
    ): NavigationStep()
    data class OutDoorMaps(
        val path: List<Coordinates>,
        val fromBuilding: Int? = null,
        val toBuilding: Int? = null,
        val fromDescription: String? = null,
        val toDescription: String? = null
    ): NavigationStep()
    data class TransitionToInDoor(
        val toBuilding: Int
    ): NavigationStep()
}

data class Coordinates(
    val latitude: Double,
    val longitude: Double
)

data class NavigationDirection(
    val steps: List<NavigationStep>,
    val totalDistanceMeters: Double,
    val badge: String?
) {
    val estimatedTimeMinutes: Double
        get() = totalDistanceMeters / 80.0
}