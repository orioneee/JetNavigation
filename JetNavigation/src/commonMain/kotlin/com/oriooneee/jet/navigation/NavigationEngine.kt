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

class NavigationEngine(private val masterNav: MasterNavigation) {
    private val outputWidth = 2000.0
    private val paddingPct = 0.05

    private val inDoorNodesMap: Map<String, InDoorNode> = masterNav.inDoorNavGraph.nodes.associateBy { it.id }
    private val outDoorNodesMap: Map<String, OutDoorNode> = masterNav.outDoorNavGraph.nodes.associateBy { it.id }
    private val globalAdjacency: Map<String, List<Pair<String, Double>>> = buildGlobalAdjacencyMap()

    init {
        Log.d(TAG, "NavigationEngine INIT (Unified Graph)")
        Log.d(TAG, "Total nodes: ${inDoorNodesMap.size + outDoorNodesMap.size}")
        Log.d(TAG, "Total connections: ${globalAdjacency.values.sumOf { it.size }}")
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
        referenceNode: InDoorNode?
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

    fun getRoute(from: InDoorNode, to: InDoorNode): NavigationDirection {
        return getRoute(ResolvedNode.InDoor(from), ResolvedNode.InDoor(to))
    }

    fun getRoute(from: ResolvedNode, to: ResolvedNode): NavigationDirection {
        Log.d(TAG, "getRoute: ${from.id} -> ${to.id}")

        val pathIds = findPathGlobal(from.id, to.id)
        if (pathIds == null) {
            Log.e(TAG, "No path found")
            return NavigationDirection(emptyList(), 0.0)
        }

        val resolvedPath = pathIds.mapNotNull { resolveNodeById(it) }
        val totalDistance = calculateTotalDistanceGlobal(resolvedPath)
        val steps = buildStepsFromUnifiedPath(resolvedPath)

        return NavigationDirection(steps, totalDistance)
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

            globalAdjacency[u]?.forEach { (v, weight) ->
                val alt = d + weight
                if (alt < (distances[v] ?: Double.MAX_VALUE)) {
                    distances[v] = alt
                    pq.offer(v to alt)
                }
            }
        }
        return null
    }

    private fun findPathGlobal(startId: String, endId: String): List<String>? {
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

            globalAdjacency[u]?.forEach { (v, weight) ->
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

    private fun resolveNodeById(id: String): ResolvedNode? {
        inDoorNodesMap[id]?.let { return ResolvedNode.InDoor(it) }
        outDoorNodesMap[id]?.let { return ResolvedNode.OutDoor(it) }
        return null
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

            if (i > 0) {
                val prevSegment = segments[i - 1]
                val prevLast = prevSegment.last()

                if (prevLast is ResolvedNode.InDoor && firstNode is ResolvedNode.OutDoor) {
                    steps.add(NavigationStep.TransitionToOutDoor(fromBuilding = prevLast.node.buildNum))
                } else if (prevLast is ResolvedNode.OutDoor && firstNode is ResolvedNode.InDoor) {
                    steps.add(NavigationStep.TransitionToInDoor(toBuilding = firstNode.node.buildNum))
                } else if (prevLast is ResolvedNode.InDoor && firstNode is ResolvedNode.InDoor) {
                    if (prevLast.node.buildNum != firstNode.node.buildNum) {
                        steps.add(NavigationStep.TransitionToBuilding(
                            prevLast.node.buildNum,
                            firstNode.node.buildNum
                        ))
                    } else if (prevLast.node.floorNum != firstNode.node.floorNum) {
                        steps.add(NavigationStep.TransitionToFlor(
                            firstNode.node.floorNum,
                            prevLast.node.floorNum
                        ))
                    }
                }
            }

            if (firstNode is ResolvedNode.InDoor) {
                val indoorNodes = segment.mapNotNull { (it as? ResolvedNode.InDoor)?.node }
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
            } else if (firstNode is ResolvedNode.OutDoor) {
                val coords = segment.mapNotNull { (it as? ResolvedNode.OutDoor)?.node }.map {
                    Coordinates(it.lat, it.lon)
                }
                steps.add(NavigationStep.OutDoorMaps(coords))
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