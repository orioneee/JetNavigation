package com.oriooneee.jet.navigation.engine.routing

import com.oriooneee.jet.navigation.domain.entities.graph.InDoorNode
import com.oriooneee.jet.navigation.domain.entities.graph.NodeType
import com.oriooneee.jet.navigation.domain.entities.graph.OutDoorNode
import com.oriooneee.jet.navigation.engine.models.ResolvedNode
import com.oriooneee.jet.navigation.engine.utils.MinHeap
import kotlin.math.abs
import kotlin.math.sqrt

class PathFinder(
    private val inDoorNodesMap: Map<String, InDoorNode>,
    private val outDoorNodesMap: Map<String, OutDoorNode>,
    private val globalAdjacency: Map<String, List<Pair<String, Double>>>
) {
    companion object {
        private const val STAIRS_PENALTY = 15.0
        private const val ENTRANCE_EXIT_PENALTY = 10.0
        private const val BUILDING_TRANSFER_PENALTY = 20.0
        private const val FLOOR_CHANGE_PENALTY = 8.0
        private const val CLIMB_PENALTY_FACTOR = 2.0
    }

    fun findNearestNodeGlobal(
        startId: String,
        criteria: (ResolvedNode) -> Boolean
    ): ResolvedNode? {
        val distances = mutableMapOf<String, Double>()
        val pq = MinHeap<Pair<String, Double>> { a, b -> a.second.compareTo(b.second) }

        distances[startId] = 0.0
        pq.offer(startId to 0.0)

        val visited = mutableSetOf<String>()

        while (pq.isNotEmpty()) {
            val (u, d) = pq.poll() ?: break

            if (u in visited) continue
            visited.add(u)

            if (d > (distances[u] ?: Double.MAX_VALUE)) continue

            val resolvedNode = resolveNodeById(u)
            if (resolvedNode != null && u != startId && criteria(resolvedNode)) {
                return resolvedNode
            }

            globalAdjacency[u]?.forEach { (v, baseWeight) ->
                val resolvedV = resolveNodeById(v)
                if (isDestinationOnlyNode(v, startId, startId) && (resolvedV == null || !criteria(resolvedV))) {
                    return@forEach
                }

                val transitionPenalty = calculateTransitionPenalty(u, v)
                val alt = d + baseWeight + transitionPenalty
                if (alt < (distances[v] ?: Double.MAX_VALUE)) {
                    distances[v] = alt
                    pq.offer(v to alt)
                }
            }
        }
        return null
    }

    fun findPathVariant(
        startId: String,
        endId: String,
        costModifier: (String, String) -> Double
    ): List<String>? {
        val distances = mutableMapOf<String, Double>()
        val previous = mutableMapOf<String, String>()
        val pq = MinHeap<Pair<String, Double>> { a, b -> a.second.compareTo(b.second) }

        distances[startId] = 0.0
        pq.offer(startId to 0.0)

        val visited = mutableSetOf<String>()

        while (pq.isNotEmpty()) {
            val (u, d) = pq.poll() ?: break

            if (u in visited) continue
            visited.add(u)

            if (d > (distances[u] ?: Double.MAX_VALUE)) continue
            if (u == endId) break

            globalAdjacency[u]?.forEach { (v, baseWeight) ->
                if (v != endId && isDestinationOnlyNode(v, startId, endId)) return@forEach

                val modifier = costModifier(u, v)
                val transitionPenalty = calculateTransitionPenalty(u, v)
                val weight = baseWeight * modifier + transitionPenalty

                val alt = d + weight
                if (alt < (distances[v] ?: Double.MAX_VALUE)) {
                    distances[v] = alt
                    previous[v] = u
                    pq.offer(v to alt)
                }
            }
        }

        if (!previous.containsKey(endId) && startId != endId) return null

        val path = mutableListOf<String>()
        var current: String? = endId
        while (current != null) {
            path.add(current)
            current = previous[current]
        }
        return path.reversed()
    }

    fun resolveNodeById(id: String): ResolvedNode? {
        inDoorNodesMap[id]?.let { return ResolvedNode.InDoor(it) }
        outDoorNodesMap[id]?.let { return ResolvedNode.OutDoor(it) }
        return null
    }

    private fun isDestinationOnlyNode(nodeId: String, startId: String, endId: String): Boolean {
        val indoorNode = inDoorNodesMap[nodeId] ?: return false
        if (!indoorNode.type.contains(NodeType.AUDITORIUM)) return false

        val nodeDigits = nodeId.filter { it.isDigit() }
        if (nodeDigits.isNotEmpty()) {
            val startDigits = startId.filter { it.isDigit() }
            val endDigits = endId.filter { it.isDigit() }
            if (nodeDigits == startDigits || nodeDigits == endDigits) {
                return false
            }
        }
        return true
    }

    private fun calculateTransitionPenalty(fromId: String, toId: String): Double {
        val fromNode = resolveNodeById(fromId)
        val toNode = resolveNodeById(toId)

        if (fromNode == null || toNode == null) return 0.0

        var penalty = 0.0

        if (fromNode is ResolvedNode.InDoor && fromNode.node.type.contains(NodeType.STAIRS)) {
            penalty += STAIRS_PENALTY
        }
        if (toNode is ResolvedNode.InDoor && toNode.node.type.contains(NodeType.STAIRS)) {
            penalty += STAIRS_PENALTY
        }

        val isEntranceExit = (fromNode is ResolvedNode.InDoor && toNode is ResolvedNode.OutDoor) ||
                (fromNode is ResolvedNode.OutDoor && toNode is ResolvedNode.InDoor)
        if (isEntranceExit) {
            penalty += ENTRANCE_EXIT_PENALTY
        }

        if (fromNode is ResolvedNode.InDoor && fromNode.node.type.contains(NodeType.MAIN_ENTRANCE)) {
            penalty += ENTRANCE_EXIT_PENALTY / 2
        }
        if (toNode is ResolvedNode.InDoor && toNode.node.type.contains(NodeType.MAIN_ENTRANCE)) {
            penalty += ENTRANCE_EXIT_PENALTY / 2
        }

        if (fromNode is ResolvedNode.InDoor && toNode is ResolvedNode.InDoor) {
            if (fromNode.node.buildNum != toNode.node.buildNum) {
                penalty += BUILDING_TRANSFER_PENALTY
            }
            if (fromNode.node.buildNum == toNode.node.buildNum &&
                fromNode.node.floorNum != toNode.node.floorNum) {
                penalty += FLOOR_CHANGE_PENALTY
            }
        }

        if (fromNode is ResolvedNode.InDoor && fromNode.node.type.contains(NodeType.TRANSFER_TO_ANOTHER_BUILDING)) {
            penalty += BUILDING_TRANSFER_PENALTY / 2
        }
        if (toNode is ResolvedNode.InDoor && toNode.node.type.contains(NodeType.TRANSFER_TO_ANOTHER_BUILDING)) {
            penalty += BUILDING_TRANSFER_PENALTY / 2
        }

        if (fromNode is ResolvedNode.InDoor && toNode is ResolvedNode.InDoor) {
            val dz = abs(toNode.node.z - fromNode.node.z)
            if (dz > 0.0) {
                val dx = toNode.node.x - fromNode.node.x
                val dy = toNode.node.y - fromNode.node.y
                val horizontalDist = sqrt(dx * dx + dy * dy)
                val slope = if (horizontalDist > 0.0) dz / horizontalDist else dz
                penalty += slope * CLIMB_PENALTY_FACTOR
            }
        }

        return penalty
    }
}
