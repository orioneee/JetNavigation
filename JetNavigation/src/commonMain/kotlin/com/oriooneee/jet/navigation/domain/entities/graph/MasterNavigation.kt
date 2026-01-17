package com.oriooneee.jet.navigation.domain.entities.graph


import jetnavigation.jetnavigation.generated.resources.Res
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MasterNavigation(
    @SerialName("buildings")
    val buildings: List<Building>,
    @SerialName("navGraph")
    val navGraph: NavGraph
) {
    companion object {
        suspend fun loadFromAssets(): MasterNavigation {
            val bytes = Res.readBytes("files/master_navigation.json")
            val jsonString = bytes.decodeToString()
            return Json.decodeFromString<MasterNavigation>(jsonString).also {
                val audsCout =
                    it.navGraph.nodes.filter { it.type.contains(NodeType.AUDITORIUM) }.size
                println("Loaded MasterNavigation with ${it.buildings.size} buildings and $audsCout auditoriums")
            }
        }
    }
}