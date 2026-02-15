package com.oriooneee.jet.navigation.domain.entities.graph


import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class MasterNavigation(
    @SerialName("plan")
    val buildings: List<Building>,
    @SerialName("inDoor")
    val inDoorNavGraph: InDoorNavGraph,
    @SerialName("outDoor")
    val outDoorNavGraph: OutDoorNavGraph,
    @SerialName("createdAt")
    private val _createdAt: String
) {
    @Transient
    val createdAt: Instant = LocalDateTime.parse(_createdAt).toInstant(TimeZone.of("Europe/Kyiv"))
}
