package com.oriooneee.jet.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Man
import androidx.compose.material.icons.outlined.Wc
import androidx.compose.material.icons.outlined.Woman
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.oriooneee.jet.navigation.domain.entities.Coordinates
import com.oriooneee.jet.navigation.domain.entities.NavigationDirection
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import com.oriooneee.jet.navigation.domain.entities.graph.Flor
import com.oriooneee.jet.navigation.domain.entities.graph.InDoorNode
import com.oriooneee.jet.navigation.domain.entities.graph.MasterNavigation
import com.oriooneee.jet.navigation.domain.entities.graph.NodeType
import com.oriooneee.jet.navigation.domain.entities.graph.OutDoorNode
import com.oriooneee.jet.navigation.domain.entities.graph.SelectNodeResult
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Log {
    fun d(tag: String, message: String) {
        println("DEBUG: [$tag] $message")
    }

    fun e(tag: String, message: String) {
        println("ERROR: [$tag] $message")
    }
}

private const val TAG = "NavEngine"

data class TextLabel(
    val text: String,
    val x: Float,
    val y: Float,
    val color: String = "#000000",
    val bold: Boolean = false,
    val hasBackground: Boolean = false
)

data class IconLabel(
    val icon: ImageVector,
    val x: Float,
    val y: Float,
    val tint: Color,
)

data class FloorRenderData(
    val width: Float,
    val height: Float,
    val polygons: List<List<Offset>>,
    val polylines: List<List<Offset>>,
    val singleLines: List<Pair<Offset, Offset>>,
    val routePath: List<Offset>,
    val startNode: Offset?,
    val endNode: Offset?,
    val textLabels: List<TextLabel>,
    val icons: List<IconLabel>,
)

sealed class ResolvedNode {
    data class InDoor(val node: InDoorNode) : ResolvedNode()
    data class OutDoor(val node: OutDoorNode) : ResolvedNode()

    val label: String?
        get() = when (this) {
            is InDoor -> node.label
            is OutDoor -> node.label
        }

    val id: String
        get() = when (this) {
            is InDoor -> node.id
            is OutDoor -> node.id
        }
}

class NavigationEngine(
    private val masterNav: MasterNavigation,
    private val isRecommendedIndoor: Boolean
) {
    private val outputWidth = 2000.0
    private val paddingPct = 0.05

    // Штрафы для маршрутизации (не входят в total distance)
    companion object {
        private const val STAIRS_PENALTY = 15.0           // штраф за подъём/спуск по лестнице
        private const val ENTRANCE_EXIT_PENALTY = 10.0    // штраф за вход/выход из здания
        private const val BUILDING_TRANSFER_PENALTY = 20.0 // штраф за переход между зданиями
        private const val FLOOR_CHANGE_PENALTY = 8.0      // штраф за смену этажа
    }

    private val inDoorNodesMap: Map<String, InDoorNode> = masterNav.inDoorNavGraph.nodes.associateBy { it.id }
    private val outDoorNodesMap: Map<String, OutDoorNode> = masterNav.outDoorNavGraph.nodes.associateBy { it.id }
    private val globalAdjacency: Map<String, List<Pair<String, Double>>> = buildGlobalAdjacencyMap()

    init {
        Log.d(TAG, "NavigationEngine INIT")
    }

    private fun buildGlobalAdjacencyMap(): Map<String, List<Pair<String, Double>>> {
        val adj = mutableMapOf<String, MutableList<Pair<String, Double>>>()

        masterNav.inDoorNavGraph.edges.forEach { edge ->
            adj.getOrPut(edge.from) { mutableListOf() }.add(edge.to to edge.weight)
            adj.getOrPut(edge.to) { mutableListOf() }.add(edge.from to edge.weight)
        }

        masterNav.outDoorNavGraph.edges.forEach { edge ->
            adj.getOrPut(edge.from) { mutableListOf() }.add(edge.to to edge.weight)
            adj.getOrPut(edge.to) { mutableListOf() }.add(edge.from to edge.weight)
        }

        val indoorEntrances = inDoorNodesMap.values.filter { it.type.contains(NodeType.MAIN_ENTRANCE) }

        indoorEntrances.forEach { indoorNode ->
            val outdoorNode = findMatchingOutdoorNode(indoorNode)
            if (outdoorNode != null) {
                val connectionWeight = 5.0
                adj.getOrPut(indoorNode.id) { mutableListOf() }.add(outdoorNode.id to connectionWeight)
                adj.getOrPut(outdoorNode.id) { mutableListOf() }.add(indoorNode.id to connectionWeight)
            }
        }

        return adj
    }

    private fun findMatchingOutdoorNode(entrance: InDoorNode): OutDoorNode? {
        val exactMatch = outDoorNodesMap.values
            .filter { it.type.contains(NodeType.MAIN_ENTRANCE) }
            .find { it.label?.contains("${entrance.buildNum}") == true }

        if (exactMatch != null) return exactMatch

        val candidates = outDoorNodesMap.values.filter { it.type.contains(NodeType.MAIN_ENTRANCE) }
        if (candidates.isEmpty()) return null

        return candidates.firstOrNull()
    }

    fun resolveSelection(
        result: SelectNodeResult,
        referenceNode: ResolvedNode?
    ): ResolvedNode? {
        return when (result) {
            is SelectNodeResult.SelectedNode -> ResolvedNode.InDoor(result.node)
            is SelectNodeResult.SelectedOutDoorNode -> ResolvedNode.OutDoor(result.node)
            is SelectNodeResult.NearestManWC -> {
                if (referenceNode == null) return null
                findNearestNodeGlobal(referenceNode.id) { resolved ->
                    resolved is ResolvedNode.InDoor && resolved.node.type.contains(NodeType.WC_MAN)
                }
            }
            is SelectNodeResult.NearestWomanWC -> {
                if (referenceNode == null) return null
                findNearestNodeGlobal(referenceNode.id) { resolved ->
                    resolved is ResolvedNode.InDoor && resolved.node.type.contains(NodeType.WC_WOMAN)
                }
            }
            is SelectNodeResult.NearestMainEntrance -> {
                if (referenceNode == null) return null
                findNearestNodeGlobal(referenceNode.id) { resolved ->
                    resolved is ResolvedNode.InDoor && resolved.node.type.contains(NodeType.MAIN_ENTRANCE)
                }
            }
        }
    }

    fun getRoute(from: InDoorNode, to: InDoorNode): List<NavigationDirection> {
        return getRoute(ResolvedNode.InDoor(from), ResolvedNode.InDoor(to))
    }

    fun getRoute(from: ResolvedNode, to: ResolvedNode): List<NavigationDirection> {
        val allPaths = mutableListOf<List<String>>()

        val standardPath = findPathVariant(from.id, to.id) { _, _ -> 1.0 }
        if (standardPath != null) allPaths.add(standardPath)

        val indoorPreferredPath = findPathVariant(from.id, to.id) { u, v ->
            if (outDoorNodesMap.containsKey(u) || outDoorNodesMap.containsKey(v)) 50.0 else 1.0
        }
        if (indoorPreferredPath != null) allPaths.add(indoorPreferredPath)

        val outdoorPreferredPath = findPathVariant(from.id, to.id) { u, v ->
            if (inDoorNodesMap.containsKey(u) && inDoorNodesMap.containsKey(v)) 50.0 else 1.0
        }
        if (outdoorPreferredPath != null) allPaths.add(outdoorPreferredPath)

        if (allPaths.size < 4 && standardPath != null) {
            val standardEdges = standardPath.zipWithNext().toSet()
            val alternativePath = findPathVariant(from.id, to.id) { u, v ->
                if (standardEdges.contains(u to v) || standardEdges.contains(v to u)) 3.0 else 1.0
            }
            if (alternativePath != null) allPaths.add(alternativePath)
        }

        val uniquePaths = allPaths.distinct()

        data class PathEvaluation(
            val steps: List<NavigationStep>,
            val totalDist: Double,
            val outdoorDist: Double
        )

        val evaluatedPaths = uniquePaths.map { pathIds ->
            val resolvedPath = pathIds.mapNotNull { resolveNodeById(it) }
            val totalDistance = calculateTotalDistanceGlobal(resolvedPath)
            val outdoorDistance = calculateOutdoorDistance(resolvedPath)
            val steps = buildStepsFromUnifiedPath(resolvedPath)

            PathEvaluation(steps, totalDistance, outdoorDistance)
        }

        if (evaluatedPaths.isEmpty()) return emptyList()

        val fastestDist = evaluatedPaths.minOf { it.totalDist }
        val maxAcceptableDist = fastestDist * 1.3

        val sortedPaths = evaluatedPaths.sortedWith { a, b ->
            if (isRecommendedIndoor) {
                val aAcceptable = a.totalDist <= maxAcceptableDist
                val bAcceptable = b.totalDist <= maxAcceptableDist

                when {
                    aAcceptable && !bAcceptable -> -1
                    !aAcceptable && bAcceptable -> 1
                    else -> {
                        val outdoorDiff = a.outdoorDist - b.outdoorDist
                        if (abs(outdoorDiff) > 10.0) {
                            a.outdoorDist.compareTo(b.outdoorDist)
                        } else {
                            a.totalDist.compareTo(b.totalDist)
                        }
                    }
                }
            } else {
                a.totalDist.compareTo(b.totalDist)
            }
        }

        val minOutdoorDist = evaluatedPaths.minOf { it.outdoorDist }

        return sortedPaths.take(4).map { path ->
            val badge = when {
                path.totalDist == fastestDist -> "Fastest"
                path.outdoorDist == minOutdoorDist && path.outdoorDist < path.totalDist * 0.5 -> "Mostly Indoor"
                isRecommendedIndoor && path.outdoorDist == minOutdoorDist -> "Recommended"
                path.outdoorDist > 0 && path.outdoorDist < path.totalDist * 0.3 -> "Balanced"
                else -> null
            }
            NavigationDirection(path.steps, path.totalDist, badge)
        }
    }

    private fun findNearestNodeGlobal(
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

                // Добавляем штраф за переходы при поиске ближайшего узла
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

    private fun findPathVariant(
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
                // Добавляем штраф за переходы (лестницы, входы/выходы и т.д.)
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

    private fun calculateTotalDistanceGlobal(path: List<ResolvedNode>): Double {
        var distance = 0.0
        for (i in 0 until path.size - 1) {
            val u = path[i]
            val v = path[i + 1]

            val edgeWeight = globalAdjacency[u.id]?.find { it.first == v.id }?.second
            if (edgeWeight != null) {
                distance += edgeWeight
            } else if (u is ResolvedNode.OutDoor && v is ResolvedNode.OutDoor) {
                distance += haversineDistance(u.node.lat, u.node.lon, v.node.lat, v.node.lon)
            }
        }
        return distance
    }

    private fun calculateOutdoorDistance(path: List<ResolvedNode>): Double {
        var distance = 0.0
        for (i in 0 until path.size - 1) {
            val u = path[i]
            val v = path[i + 1]

            if (u is ResolvedNode.OutDoor || v is ResolvedNode.OutDoor) {
                val edgeWeight = globalAdjacency[u.id]?.find { it.first == v.id }?.second
                if (edgeWeight != null) {
                    distance += edgeWeight
                } else if (u is ResolvedNode.OutDoor && v is ResolvedNode.OutDoor) {
                    distance += haversineDistance(u.node.lat, u.node.lon, v.node.lat, v.node.lon)
                }
            }
        }
        return distance
    }

    private fun resolveNodeById(id: String): ResolvedNode? {
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

    /**
     * Вычисляет штраф за переход между двумя узлами.
     * Эти штрафы используются только для маршрутизации и не входят в отображаемую дистанцию.
     */
    private fun calculateTransitionPenalty(fromId: String, toId: String): Double {
        val fromNode = resolveNodeById(fromId)
        val toNode = resolveNodeById(toId)

        if (fromNode == null || toNode == null) return 0.0

        var penalty = 0.0

        // Штраф за лестницы
        if (fromNode is ResolvedNode.InDoor && fromNode.node.type.contains(NodeType.STAIRS)) {
            penalty += STAIRS_PENALTY
        }
        if (toNode is ResolvedNode.InDoor && toNode.node.type.contains(NodeType.STAIRS)) {
            penalty += STAIRS_PENALTY
        }

        // Штраф за вход/выход из здания (переход indoor <-> outdoor)
        val isEntranceExit = (fromNode is ResolvedNode.InDoor && toNode is ResolvedNode.OutDoor) ||
                (fromNode is ResolvedNode.OutDoor && toNode is ResolvedNode.InDoor)
        if (isEntranceExit) {
            penalty += ENTRANCE_EXIT_PENALTY
        }

        // Штраф за главный вход (дополнительно, если проходим через MAIN_ENTRANCE)
        if (fromNode is ResolvedNode.InDoor && fromNode.node.type.contains(NodeType.MAIN_ENTRANCE)) {
            penalty += ENTRANCE_EXIT_PENALTY / 2
        }
        if (toNode is ResolvedNode.InDoor && toNode.node.type.contains(NodeType.MAIN_ENTRANCE)) {
            penalty += ENTRANCE_EXIT_PENALTY / 2
        }

        // Штраф за переход между зданиями
        if (fromNode is ResolvedNode.InDoor && toNode is ResolvedNode.InDoor) {
            if (fromNode.node.buildNum != toNode.node.buildNum) {
                penalty += BUILDING_TRANSFER_PENALTY
            }
            // Штраф за смену этажа (в пределах одного здания)
            if (fromNode.node.buildNum == toNode.node.buildNum &&
                fromNode.node.floorNum != toNode.node.floorNum) {
                penalty += FLOOR_CHANGE_PENALTY
            }
        }

        // Штраф за узел перехода между зданиями
        if (fromNode is ResolvedNode.InDoor && fromNode.node.type.contains(NodeType.TRANSFER_TO_ANOTHER_BUILDING)) {
            penalty += BUILDING_TRANSFER_PENALTY / 2
        }
        if (toNode is ResolvedNode.InDoor && toNode.node.type.contains(NodeType.TRANSFER_TO_ANOTHER_BUILDING)) {
            penalty += BUILDING_TRANSFER_PENALTY / 2
        }

        return penalty
    }

    private fun buildStepsFromUnifiedPath(path: List<ResolvedNode>): List<NavigationStep> {
        val steps = mutableListOf<NavigationStep>()
        if (path.isEmpty()) return steps

        val segments = mutableListOf<List<ResolvedNode>>()
        var currentSegment = mutableListOf<ResolvedNode>()

        path.forEachIndexed { index, node ->
            if (index == 0) {
                currentSegment.add(node)
            } else {
                val prev = path[index - 1]
                val isSplit = when {
                    prev is ResolvedNode.InDoor && node is ResolvedNode.OutDoor -> true
                    prev is ResolvedNode.OutDoor && node is ResolvedNode.InDoor -> true
                    prev is ResolvedNode.InDoor && node is ResolvedNode.InDoor -> {
                        prev.node.buildNum != node.node.buildNum || prev.node.floorNum != node.node.floorNum
                    }
                    else -> false
                }

                if (isSplit) {
                    segments.add(ArrayList(currentSegment))
                    currentSegment = mutableListOf()
                }
                currentSegment.add(node)
            }
        }
        if (currentSegment.isNotEmpty()) {
            segments.add(currentSegment)
        }

        val globalStart = path.first()
        val globalEnd = path.last()

        segments.forEachIndexed { i, segment ->
            val firstNode = segment.first()
            val indoorNodes = segment.mapNotNull { (it as? ResolvedNode.InDoor)?.node }
            val isSingleIndoorPoint = firstNode is ResolvedNode.InDoor && indoorNodes.size <= 1
            val isLastSegment = i == segments.lastIndex

            if (i > 0) {
                val prevSegment = segments[i - 1]
                val prevLast = prevSegment.last()

                if (prevLast is ResolvedNode.InDoor && firstNode is ResolvedNode.OutDoor) {
                    val prevIndoorCount = prevSegment.count { it is ResolvedNode.InDoor }
                    val isPrevStartSingle = (i - 1 == 0) && prevIndoorCount <= 1

                    if (!isPrevStartSingle) {
                        steps.add(NavigationStep.TransitionToOutDoor(fromBuilding = prevLast.node.buildNum))
                    }
                } else if (prevLast is ResolvedNode.OutDoor && firstNode is ResolvedNode.InDoor) {
                    val skipTransition = (isLastSegment && isSingleIndoorPoint)
                    if (!skipTransition) {
                        steps.add(NavigationStep.TransitionToInDoor(toBuilding = firstNode.node.buildNum))
                    }
                } else if (prevLast is ResolvedNode.InDoor && firstNode is ResolvedNode.InDoor) {
                    val prevIndoorCount = prevSegment.count { it is ResolvedNode.InDoor }
                    val isPrevStartSingle = (i - 1 == 0) && prevIndoorCount <= 1

                    if (!isPrevStartSingle) {
                        if (prevLast.node.buildNum != firstNode.node.buildNum) {
                            steps.add(NavigationStep.TransitionToBuilding(
                                prevLast.node.buildNum,
                                firstNode.node.buildNum
                            ))
                        } else if (prevLast.node.floorNum != firstNode.node.floorNum) {
                            val skipTransitionToLastSingle = isLastSegment && isSingleIndoorPoint
                            if (!skipTransitionToLastSingle) {
                                steps.add(NavigationStep.TransitionToFlor(
                                    firstNode.node.floorNum,
                                    prevLast.node.floorNum
                                ))
                            }
                        }
                    }
                }
            }

            if (firstNode is ResolvedNode.InDoor) {
                val isStartSegment = i == 0
                val isEndSegment = i == segments.lastIndex

                val nextIsOutdoor = (i + 1 < segments.size) && (segments[i + 1].first() is ResolvedNode.OutDoor)
                val prevIsOutdoor = (i - 1 >= 0) && (segments[i - 1].last() is ResolvedNode.OutDoor)
                val nextIsIndoor = (i + 1 < segments.size) && (segments[i + 1].first() is ResolvedNode.InDoor)
                val prevIsIndoor = (i - 1 >= 0) && (segments[i - 1].last() is ResolvedNode.InDoor)

                val skipStartOutdoor = isStartSegment && isSingleIndoorPoint && nextIsOutdoor
                val skipStartIndoor = isStartSegment && isSingleIndoorPoint && nextIsIndoor
                val skipEnd = isEndSegment && isSingleIndoorPoint
                val skipIntermediate = !isStartSegment && !isEndSegment && isSingleIndoorPoint && prevIsIndoor && nextIsIndoor

                if (!skipStartOutdoor && !skipStartIndoor && !skipEnd && !skipIntermediate) {
                    val buildingId = firstNode.node.buildNum
                    val floorNum = firstNode.node.floorNum

                    val building = masterNav.buildings.find { it.num == buildingId }
                    val flor = building?.flors?.find { it.num == floorNum }

                    if (flor != null) {
                        val startNodeForRender = if (segment.first().id == globalStart.id) {
                            (globalStart as? ResolvedNode.InDoor)?.node ?: indoorNodes.first()
                        } else indoorNodes.first()

                        val endNodeForRender = if (segment.last().id == globalEnd.id) {
                            (globalEnd as? ResolvedNode.InDoor)?.node ?: indoorNodes.last()
                        } else indoorNodes.last()

                        val renderData = generateFloorData(
                            flor = flor,
                            buildingId = buildingId,
                            stepPath = indoorNodes,
                            localStart = startNodeForRender,
                            localEnd = endNodeForRender
                        )

                        val focusPoint = renderData.startNode ?: renderData.routePath.firstOrNull() ?: Offset.Zero

                        steps.add(
                            NavigationStep.ByFlor(
                                flor = floorNum,
                                building = buildingId,
                                image = renderData,
                                pointOfInterest = focusPoint,
                                textLabels = renderData.textLabels
                            )
                        )
                    }
                }
            } else if (firstNode is ResolvedNode.OutDoor) {
                val outdoorNodes = segment.mapNotNull { (it as? ResolvedNode.OutDoor)?.node }
                val coords = outdoorNodes.map { Coordinates(it.lat, it.lon) }

                val prevSegment = segments.getOrNull(i - 1)
                val nextSegment = segments.getOrNull(i + 1)

                val fromBuilding = (prevSegment?.lastOrNull() as? ResolvedNode.InDoor)?.node?.buildNum
                val toBuilding = (nextSegment?.firstOrNull() as? ResolvedNode.InDoor)?.node?.buildNum

                val lastOutdoorNode = outdoorNodes.lastOrNull()
                val isLastSegment = i == segments.lastIndex
                val destinationLabel = if (isLastSegment) {
                    (globalEnd as? ResolvedNode.OutDoor)?.node?.label
                } else {
                    lastOutdoorNode?.label
                }

                val fromDescription = fromBuilding?.let { "Exit building $it" }
                val toDescription = when {
                    destinationLabel != null -> "Go to $destinationLabel"
                    toBuilding != null -> "Go to entrance of building $toBuilding"
                    else -> null
                }

                steps.add(NavigationStep.OutDoorMaps(
                    path = coords,
                    fromBuilding = fromBuilding,
                    toBuilding = toBuilding,
                    fromDescription = fromDescription,
                    toDescription = toDescription
                ))
            }
        }

        return steps
    }

    private fun generateFloorData(
        flor: Flor,
        buildingId: Int,
        stepPath: List<InDoorNode>,
        localStart: InDoorNode,
        localEnd: InDoorNode
    ): FloorRenderData {
        val allX = mutableListOf<Double>()
        val allY = mutableListOf<Double>()

        flor.plan.polylines.forEach { p ->
            p.points.forEach { pt ->
                allX.add(pt[0])
                allY.add(pt[1])
            }
        }
        flor.plan.lines.forEach { l ->
            allX.add(l.x1)
            allX.add(l.x2)
            allY.add(l.y1)
            allY.add(l.y2)
        }
        flor.plan.texts.forEach { t ->
            allX.add(t.x)
            allY.add(t.y)
        }

        if (allX.isEmpty()) {
            return FloorRenderData(
                1f,
                1f,
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                null,
                null,
                emptyList(),
                emptyList()
            )
        }

        val minX = allX.minOrNull() ?: 0.0
        val maxX = allX.maxOrNull() ?: 1.0
        val minY = allY.minOrNull() ?: 0.0
        val maxY = allY.maxOrNull() ?: 1.0

        val dataW = maxX - minX
        val dataH = maxY - minY
        if (dataW == 0.0 || dataH == 0.0) return FloorRenderData(
            1f,
            1f,
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            null,
            null,
            emptyList(),
            emptyList()
        )

        val drawWidth = outputWidth * (1 - paddingPct * 2)
        val scale = drawWidth / dataW
        val outputHeight = (dataH * scale + (outputWidth * paddingPct * 2)).toInt()
        val padding = outputWidth * paddingPct

        fun tx(x: Double): Float = ((x - minX) * scale + padding).toFloat()
        fun ty(y: Double): Float = (outputHeight - ((y - minY) * scale + padding)).toFloat()

        val polygons = mutableListOf<List<Offset>>()
        val polylines = mutableListOf<List<Offset>>()

        flor.plan.polylines.forEach { poly ->
            val points = poly.points.map { Offset(tx(it[0]), ty(it[1])) }
            if (poly.closed) polygons.add(points) else polylines.add(points)
        }

        val singleLines = flor.plan.lines.map { l ->
            Pair(Offset(tx(l.x1), ty(l.y1)), Offset(tx(l.x2), ty(l.y2)))
        }

        val routePoints = mutableListOf<Offset>()
        stepPath.forEach { node ->
            if (!node.type.contains(NodeType.STAIRS)) {
                routePoints.add(Offset(tx(node.x), ty(node.y)))
            }
        }

        var startNodeOffset: Offset? = null
        if (!localStart.type.contains(NodeType.STAIRS)) {
            startNodeOffset = Offset(tx(localStart.x), ty(localStart.y))
        }

        var endNodeOffset: Offset? = null
        if (!localEnd.type.contains(NodeType.STAIRS)) {
            endNodeOffset = Offset(tx(localEnd.x), ty(localEnd.y))
        }

        val icons = mutableListOf<IconLabel>()
        val floorNodes = masterNav.inDoorNavGraph.nodes.filter {
            it.buildNum == buildingId && it.floorNum == flor.num
        }

        floorNodes.forEach { node ->
            var icon: ImageVector? = null
            var tint: Color = Color.Black

            when {
                node.type.containsAll(listOf(NodeType.WC_WOMAN, NodeType.WC_MAN)) -> {
                    icon = Icons.Outlined.Wc
                    tint = Color(0xFF9B27AF)
                }
                node.type.contains(NodeType.WC_MAN) -> {
                    icon = Icons.Outlined.Man
                    tint = Color(0xFF4A90E2)
                }
                node.type.contains(NodeType.WC_WOMAN) -> {
                    icon = Icons.Outlined.Woman
                    tint = Color(0xFFE91E63)
                }
                node.type.contains(NodeType.MAIN_ENTRANCE) -> {
                    icon = Icons.Outlined.ExitToApp
                    tint = Color(0xFF4CAF50)
                }
            }

            if (icon != null) {
                icons.add(IconLabel(icon, tx(node.x), ty(node.y), tint))
            }
        }

        val textLabels = mutableListOf<TextLabel>()
        flor.plan.texts.forEach { txt ->
            var clean = txt.text.filter { !it.isISOControl() }
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .trim()

            val isWcText = clean.contains("wc", ignoreCase = true) ||
                    clean.equals("м", ignoreCase = true) ||
                    clean.equals("ж", ignoreCase = true)

            if (clean.isNotEmpty() && !isWcText) {
                textLabels.add(TextLabel(clean, tx(txt.x), ty(txt.y), "#666666"))
            }
        }

        return FloorRenderData(
            width = outputWidth.toFloat(),
            height = outputHeight.toFloat(),
            polygons = polygons,
            polylines = polylines,
            singleLines = singleLines,
            routePath = routePoints,
            startNode = startNodeOffset,
            endNode = endNodeOffset,
            textLabels = textLabels,
            icons = icons
        )
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = (lat2 - lat1) * kotlin.math.PI / 180.0
        val dLon = (lon2 - lon1) * kotlin.math.PI / 180.0
        val lat1Rad = lat1 * kotlin.math.PI / 180.0
        val lat2Rad = lat2 * kotlin.math.PI / 180.0
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private class MinHeap<T>(private val comparator: Comparator<T>) {
        private val heap = ArrayList<T>()

        fun isNotEmpty(): Boolean = heap.isNotEmpty()

        fun offer(element: T) {
            heap.add(element)
            siftUp(heap.size - 1)
        }

        fun poll(): T? {
            if (heap.isEmpty()) return null
            val result = heap[0]
            val last = heap.removeAt(heap.size - 1)
            if (heap.isNotEmpty()) {
                heap[0] = last
                siftDown(0)
            }
            return result
        }

        private fun siftUp(index: Int) {
            var k = index
            while (k > 0) {
                val parent = (k - 1) / 2
                if (comparator.compare(heap[k], heap[parent]) >= 0) break
                swap(k, parent)
                k = parent
            }
        }

        private fun siftDown(index: Int) {
            var k = index
            val half = heap.size / 2
            while (k < half) {
                var child = 2 * k + 1
                val right = child + 1
                if (right < heap.size && comparator.compare(heap[right], heap[child]) < 0) {
                    child = right
                }
                if (comparator.compare(heap[k], heap[child]) <= 0) break
                swap(k, child)
                k = child
            }
        }

        private fun swap(i: Int, j: Int) {
            val temp = heap[i]
            heap[i] = heap[j]
            heap[j] = temp
        }
    }
}