package com.oriooneee.jet.navigation.domain.entities.graph


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Flor(
    @SerialName("num")
    val num: Int,
    @SerialName("plan")
    val plan: Plan
)