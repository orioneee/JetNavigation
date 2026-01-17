package com.oriooneee.jet.navigation.domain.entities.graph

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NodeType {
    @SerialName("AUD")
    AUDITORIUM,
    @SerialName("WC_M")
    WC_MAN,
    @SerialName("WC_W")
    WC_WOMAN,
    @SerialName("MAIN_ENTERANCE")
    MAIN_ENTRANCE,
    @SerialName("TRANSFER")
    TRANSFER_TO_ANOTHER_BUILDING,
    @SerialName("STAIRS")
    STAIRS,
    @SerialName("POI")
    POINT_OF_INTEREST,
    @SerialName("TURN")
    TURN
}