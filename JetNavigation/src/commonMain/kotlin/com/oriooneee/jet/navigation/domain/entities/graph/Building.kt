package com.oriooneee.jet.navigation.domain.entities.graph


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Building(
    @SerialName("buildNum")
    private val _num: String,
    @SerialName("plan")
    val flors: List<Flor>
){
    val num: Int
        get() = _num.toInt()
}