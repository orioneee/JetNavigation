package com.oriooneee.jet.navigation.domain.entities.graph


import jetnavigation.jetnavigation.generated.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        private val mutex = Mutex()
        private var cachedInstance: MasterNavigation? = null

        suspend fun loadFromAssets(): MasterNavigation {
            cachedInstance?.let { return it }

            return mutex.withLock {
                cachedInstance ?: loadInternal().also {
                    cachedInstance = it
                }
            }
        }

        private suspend fun loadInternal(): MasterNavigation {
            val bytes = Res.readBytes("files/master_navigation.json")
            val jsonString = bytes.decodeToString()

            return Json.decodeFromString<MasterNavigation>(jsonString).also {
                val audsCount = it.navGraph.nodes
                    .count { node -> node.type.contains(NodeType.AUDITORIUM) }

                println(
                    "Loaded MasterNavigation with " +
                            "${it.buildings.size} buildings and $audsCount auditoriums"
                )
            }
        }
    }
}
