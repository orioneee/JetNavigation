package com.oriooneee.jet.navigation.domain.entities.graph


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Text(
    @SerialName("text")
    val text: String,
    @SerialName("x")
    val x: Double,
    @SerialName("y")
    val y: Double
)