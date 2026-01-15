package com.oriooneee.jet.navigation.domain.entities

import androidx.compose.ui.geometry.Offset
import com.oriooneee.jet.navigation.TextLabel

sealed class NavigationStep {
    data class ByFlor(
        val flor: Int,
        val image: ByteArray,
        val pointOfInterest: Offset = Offset.Zero,
        val textLabels: List<TextLabel> = emptyList()
    ) : NavigationStep() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as ByFlor

            if (flor != other.flor) return false
            if (!image.contentEquals(other.image)) return false
            if (pointOfInterest != other.pointOfInterest) return false
            if (textLabels != other.textLabels) return false

            return true
        }

        override fun hashCode(): Int {
            var result = flor
            result = 31 * result + image.contentHashCode()
            result = 31 * result + pointOfInterest.hashCode()
            result = 31 * result + textLabels.hashCode()
            return result
        }
    }

    data class TransitionToFlor(
        val to: Int,
        val from: Int
    ) : NavigationStep()
}

data class NavigationDirection(
    val steps: List<NavigationStep>,
    val totalDistanceMeters: Double,
) {
    val estimatedTimeMinutes: Double
        get() = totalDistanceMeters / 80.0
}