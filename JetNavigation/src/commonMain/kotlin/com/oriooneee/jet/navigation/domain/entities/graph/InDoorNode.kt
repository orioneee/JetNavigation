package com.oriooneee.jet.navigation.domain.entities.graph


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InDoorNode(
    @SerialName("id")
    override val id: String,
    @SerialName("label")
    override val label: String? = null,
    @SerialName("type")
    private val _type: List<NodeType>? = null,
    @SerialName("x")
    val x: Double,
    @SerialName("y")
    val y: Double,
    @SerialName("z")
    val z: Double,
    @SerialName("buildNum")
    private val _buildNum: String,
    @SerialName("floorNum")
    val floorNum: Int
): NavNode{
    override val type: List<NodeType>
        get() = _type ?: if(id.contains("TURN")) listOf(NodeType.TURN) else emptyList()
    override val buildNum: Int
        get() = _buildNum.toIntOrNull() ?: 0
}