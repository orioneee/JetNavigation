package com.oriooneee.jet.navigation.domain.entities.graph

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OutDoorNode(
    @SerialName("id")
    override val id: String,
    @SerialName("label")
    override val label: String? = null,
    @SerialName("type")
    private val _type: List<NodeType>? = null,
    @SerialName("lon")
    val lon: Double,
    @SerialName("lat")
    val lat: Double,
    @SerialName("buildNum")
    private val _buildNum: String? = null,
): NavNode {
    override val type: List<NodeType>
        get() = _type ?: if (id.contains("TURN")) listOf(NodeType.TURN) else emptyList()

    override val buildNum: Int?
        get() = _buildNum?.toIntOrNull()
}