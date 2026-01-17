package com.oriooneee.jet.navigation.domain.entities.graph


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Plan(
    @SerialName("lines")
    val lines: List<Line>,
    @SerialName("polylines")
    val polylines: List<Polyline>,
    @SerialName("texts")
    val texts: List<Text>
)