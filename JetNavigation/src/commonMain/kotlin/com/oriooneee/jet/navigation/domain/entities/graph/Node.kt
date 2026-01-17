package com.oriooneee.jet.navigation.domain.entities.graph


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Node(
    @SerialName("id")
    val id: String,
    @SerialName("label")
    val label: String? = null,
    @SerialName("type")
    private val _type: List<NodeType>? = null,
    @SerialName("x")
    val x: Double,
    @SerialName("y")
    val y: Double,
    @SerialName("z")
    val z: Double
){
    val type: List<NodeType>
        get() = _type ?: if(id.contains("TURN")) listOf(NodeType.TURN) else emptyList()
}