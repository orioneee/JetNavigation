package com.oriooneee.jet.navigation.domain.entities.graph


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Polyline(
    @SerialName("closed")
    val closed: Boolean,
    @SerialName("points")
    val points: List<List<Double>>
)