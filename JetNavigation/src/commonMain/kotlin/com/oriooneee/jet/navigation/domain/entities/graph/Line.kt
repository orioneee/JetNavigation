package com.oriooneee.jet.navigation.domain.entities.graph


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Line(
    @SerialName("x1")
    val x1: Double,
    @SerialName("x2")
    val x2: Double,
    @SerialName("y1")
    val y1: Double,
    @SerialName("y2")
    val y2: Double
)