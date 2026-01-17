package com.oriooneee.jet.navigation.domain.entities

import androidx.compose.ui.geometry.Offset
import com.oriooneee.jet.navigation.FloorRenderData
import com.oriooneee.jet.navigation.TextLabel

sealed class NavigationStep {
    data class ByFlor(
        val flor: Int,
        val image: FloorRenderData,
        val pointOfInterest: Offset = Offset.Zero,
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
}

data class NavigationDirection(
    val steps: List<NavigationStep>,
    val totalDistanceMeters: Double,
) {
    val estimatedTimeMinutes: Double
        get() = totalDistanceMeters / 80.0
}