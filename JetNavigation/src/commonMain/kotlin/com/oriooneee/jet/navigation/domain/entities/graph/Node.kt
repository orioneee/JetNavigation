package com.oriooneee.jet.navigation.domain.entities.graph


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Node(
    @SerialName("id")
    val id: String,
    @SerialName("x")
    val x: Double,
    @SerialName("y")
    val y: Double,
    @SerialName("z")
    val z: Double,
    @SerialName("label")
    val label: String? = null,
) {
    val type: NodeType
        get() {
            return when {
                id.contains("AUD") -> NodeType.AUDITORIUM
                id.contains("TO_BUILDING") -> NodeType.TRANSFER_TO_ANOTHER_BUILDING
                id.contains("ENTER") -> NodeType.MAIN_ENTRANCE
                id.contains("STAIRS") -> NodeType.STAIRS
                else -> NodeType.TURN
            }
        }
}


@Serializable
enum class NodeType {
    MAIN_ENTRANCE,
    TRANSFER_TO_ANOTHER_BUILDING,
    AUDITORIUM,
    TURN,
    STAIRS,
}

@Serializable
sealed class SelectNodeResult{
    @Serializable
    data object NearestManWC : SelectNodeResult()
    @Serializable
    data object NearestWomanWC : SelectNodeResult()
    @Serializable
    data object NearestMainEntrance : SelectNodeResult()
    @Serializable
    data class SelectedNode(val node: Node) : SelectNodeResult()
}