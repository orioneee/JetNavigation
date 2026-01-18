package com.oriooneee.jet.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoorSliding
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Man
import androidx.compose.material.icons.outlined.Wc
import androidx.compose.material.icons.outlined.Woman
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.oriooneee.jet.navigation.domain.entities.NavigationDirection
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import com.oriooneee.jet.navigation.domain.entities.graph.Flor
import com.oriooneee.jet.navigation.domain.entities.graph.MasterNavigation
import com.oriooneee.jet.navigation.domain.entities.graph.Node
import com.oriooneee.jet.navigation.domain.entities.graph.NodeType
import com.oriooneee.jet.navigation.domain.entities.graph.SelectNodeResult

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

class NavigationEngine(private val masterNav: MasterNavigation) {
    private val outputWidth = 2000.0
    private val paddingPct = 0.05
    private val adjacency: Map<String, List<Pair<String, Double>>> = buildAdjacencyMap()
    private val nodesMap: Map<String, Node> = masterNav.navGraph.nodes.associateBy { it.id }

    private fun buildAdjacencyMap(): Map<String, MutableList<Pair<String, Double>>> {
        val adj = mutableMapOf<String, MutableList<Pair<String, Double>>>()

        masterNav.navGraph.edges.forEach { edge ->
            adj.getOrPut(edge.from) { mutableListOf() }.add(edge.to to edge.weight)
            adj.getOrPut(edge.to) { mutableListOf() }.add(edge.from to edge.weight)
        }

        return adj
    }

    fun resolveSelection(
        result: SelectNodeResult,
        referenceNode: Node?
    ): Node? {
        return when (result) {
            is SelectNodeResult.SelectedNode -> result.node
            is SelectNodeResult.NearestManWC -> {
                if (referenceNode == null) return null
                findNearestNode(referenceNode) {
                    it.type.contains(NodeType.WC_MAN)
                }
            }

            is SelectNodeResult.NearestWomanWC -> {
                if (referenceNode == null) return null
                findNearestNode(referenceNode) {
                    it.type.contains(NodeType.WC_WOMAN)
                }
            }

            is SelectNodeResult.NearestMainEntrance -> {
                if (referenceNode == null) return null
                findNearestNode(referenceNode) {
                    it.type.contains(NodeType.MAIN_ENTRANCE)
                }
            }
        }
    }

    fun getRoute(
        from: Node,
        to: Node
    ): NavigationDirection {
        val path = findPath(from, to)

        if (path == null) {
            return NavigationDirection(emptyList(), 0.0)
        }

        val totalDistance = calculateTotalDistance(path)
        val steps = buildNavigationSteps(path)
        return NavigationDirection(steps, totalDistance)
    }

    private fun findNearestNode(
        referenceNode: Node,
        criteria: (Node) -> Boolean
    ): Node? {
        val distances = mutableMapOf<String, Double>()
        masterNav.navGraph.nodes.forEach { distances[it.id] = Double.MAX_VALUE }
        distances[referenceNode.id] = 0.0

        val pq = MinHeap<Pair<String, Double>> { a, b -> a.second.compareTo(b.second) }
        pq.offer(referenceNode.id to 0.0)

        while (pq.isNotEmpty()) {
            val (u, d) = pq.poll() ?: break

            if (d > (distances[u] ?: Double.MAX_VALUE)) continue

            val currentNode = nodesMap[u]
            if (currentNode != null && u != referenceNode.id && criteria(currentNode)) {
                return currentNode
            }

            adjacency[u]?.forEach { (v, weight) ->
                val alt = d + weight
                if (alt < (distances[v] ?: Double.MAX_VALUE)) {
                    distances[v] = alt
                    pq.offer(v to alt)
                }
            }
        }
        return null
    }

    private fun findPath(start: Node, end: Node): List<Node>? {
        val distances = mutableMapOf<String, Double>()
        val previous = mutableMapOf<String, String>()

        masterNav.navGraph.nodes.forEach { distances[it.id] = Double.MAX_VALUE }
        distances[start.id] = 0.0

        val pq = MinHeap<Pair<String, Double>> { a, b -> a.second.compareTo(b.second) }
        pq.offer(start.id to 0.0)

        val visited = mutableSetOf<String>()

        while (pq.isNotEmpty()) {
            val (u, d) = pq.poll() ?: break

            if (u in visited) continue
            visited.add(u)

            if (d > (distances[u] ?: Double.MAX_VALUE)) continue

            if (u == end.id) {
                break
            }

            val neighbors = adjacency[u] ?: continue

            neighbors.forEach { (v, weight) ->
                val node = nodesMap[v]
                if (node != null) {
                    val shouldSkip = if (node.type.contains(NodeType.AUDITORIUM)) {
                        val audNum = node.label?.filter { it.isDigit() }?.toIntOrNull()
                        val startAudNum = start.label?.filter { it.isDigit() }?.toIntOrNull()
                        val endAudNum = end.label?.filter { it.isDigit() }?.toIntOrNull()
                        if (audNum == null || startAudNum == null || endAudNum == null) {
                            false
                        } else {
                            audNum != startAudNum && audNum != endAudNum
                        }
                    } else {
                        false
                    }

                    if (!shouldSkip) {
                        val alt = d + weight
                        if (alt < (distances[v] ?: Double.MAX_VALUE)) {
                            distances[v] = alt
                            previous[v] = u
                            pq.offer(v to alt)
                        }
                    }
                }
            }
        }

        if (distances[end.id] == Double.MAX_VALUE) {
            return null
        }

        val path = mutableListOf<Node>()
        var current: String? = end.id

        while (current != null) {
            nodesMap[current]?.let { path.add(it) }
            current = previous[current]
            if (current == start.id) {
                nodesMap[start.id]?.let { path.add(it) }
                break
            }
        }

        return path.reversed()
    }

    private fun calculateTotalDistance(path: List<Node>): Double {
        var distance = 0.0
        for (i in 0 until path.size - 1) {
            val u = path[i]
            val v = path[i + 1]
            val edge = masterNav.navGraph.edges.find {
                (it.from == u.id && it.to == v.id) || (it.to == u.id && it.from == v.id)
            }
            distance += edge?.weight ?: 0.0
        }
        return distance
    }

    private data class PathSegment(
        val buildingNum: Int,
        val floorNum: Int,
        val nodes: List<Node>
    )

    private fun buildNavigationSteps(fullPath: List<Node>): List<NavigationStep> {
        val steps = mutableListOf<NavigationStep>()
        if (fullPath.isEmpty()) return steps

        val segments = groupPathByLocation(fullPath)

        val visibleSegments = segments.filter { segment ->
            segment.nodes.any { !it.type.contains(NodeType.STAIRS) }
        }

        val globalStartNode = fullPath.first()
        val globalEndNode = fullPath.last()


        visibleSegments.forEachIndexed { index, segment ->

            if (index > 0) {
                val prevSegment = visibleSegments[index - 1]

                if (prevSegment.buildingNum != segment.buildingNum) {
                    steps.add(
                        NavigationStep.TransitionToBuilding(
                            form = prevSegment.buildingNum,
                            to = segment.buildingNum
                        )
                    )
                } else if (prevSegment.floorNum != segment.floorNum) {
                    steps.add(
                        NavigationStep.TransitionToFlor(
                            to = segment.floorNum,
                            from = prevSegment.floorNum
                        )
                    )
                }
            }

            val building = masterNav.buildings.find { it.num == segment.buildingNum }
            val flor = building?.flors?.find { it.num == segment.floorNum }


            if (flor != null) {
                val renderData = generateFloorData(
                    flor = flor,
                    buildingId = segment.buildingNum,
                    stepPath = segment.nodes,
                    globalStart = globalStartNode,
                    globalEnd = globalEndNode
                )

                val focusPoint =
                    renderData.startNode ?: renderData.routePath.firstOrNull() ?: Offset.Zero

                steps.add(
                    NavigationStep.ByFlor(
                        flor = segment.floorNum,
                        building = segment.buildingNum,
                        image = renderData,
                        pointOfInterest = focusPoint,
                        textLabels = renderData.textLabels
                    )
                )
            }
        }

        return steps
    }

    private fun groupPathByLocation(path: List<Node>): List<PathSegment> {
        val segments = mutableListOf<PathSegment>()
        if (path.isEmpty()) return segments

        var currentNodes = mutableListOf<Node>()
        var (currentBuilding, currentFloor) = path.first().buildNum to path.first().floorNum

        path.forEach { node ->
            val (nodeBuilding, nodeFloor) = node.buildNum to node.floorNum

            if (nodeBuilding != currentBuilding || nodeFloor != currentFloor) {
                if (currentNodes.isNotEmpty()) {
                    segments.add(
                        PathSegment(
                            currentBuilding,
                            currentFloor,
                            ArrayList(currentNodes)
                        )
                    )
                }
                currentNodes = mutableListOf()
                currentBuilding = nodeBuilding
                currentFloor = nodeFloor
            }
            currentNodes.add(node)
        }

        if (currentNodes.isNotEmpty()) {
            segments.add(PathSegment(currentBuilding, currentFloor, currentNodes))
        }
        return segments
    }

    private fun generateFloorData(
        flor: Flor,
        buildingId: Int,
        stepPath: List<Node>,
        globalStart: Node,
        globalEnd: Node
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
                width = 1f,
                height = 1f,
                polygons = emptyList(),
                polylines = emptyList(),
                singleLines = emptyList(),
                routePath = emptyList(),
                startNode = null,
                endNode = null,
                textLabels = emptyList(),
                icons = emptyList()
            )
        }

        val minX = allX.minOrNull() ?: 0.0
        val maxX = allX.maxOrNull() ?: 1.0
        val minY = allY.minOrNull() ?: 0.0
        val maxY = allY.maxOrNull() ?: 1.0

        val dataW = maxX - minX
        val dataH = maxY - minY

        if (dataW == 0.0 || dataH == 0.0) {
            return FloorRenderData(
                width = 1f,
                height = 1f,
                polygons = emptyList(),
                polylines = emptyList(),
                singleLines = emptyList(),
                routePath = emptyList(),
                startNode = null,
                endNode = null,
                textLabels = emptyList(),
                icons = emptyList()
            )
        }

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
            if (poly.closed) {
                polygons.add(points)
            } else {
                polylines.add(points)
            }
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
        var endNodeOffset: Offset? = null

        if (stepPath.isNotEmpty() && stepPath.first().id == globalStart.id) {
            val start = stepPath.first()
            if (!start.type.contains(NodeType.STAIRS)) {
                startNodeOffset = Offset(tx(start.x), ty(start.y))
            }
        }
        if (stepPath.isNotEmpty() && stepPath.last().id == globalEnd.id) {
            val end = stepPath.last()
            if (!end.type.contains(NodeType.STAIRS)) {
                endNodeOffset = Offset(tx(end.x), ty(end.y))
            }
        }

        val icons = mutableListOf<IconLabel>()
        val floorNodes = masterNav.navGraph.nodes.filter {
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
                icons.add(
                    IconLabel(
                        icon = icon,
                        x = tx(node.x),
                        y = ty(node.y),
                        tint = tint
                    )
                )
            }
        }

        val textLabels = mutableListOf<TextLabel>()

        flor.plan.texts.forEach { txt ->
            var clean = txt.text
            val sbClean = StringBuilder()
            for (char in clean) {
                if (!char.isISOControl()) sbClean.append(char)
            }
            clean = sbClean.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .trim()

            val isWcText = clean.contains("wc", ignoreCase = true) ||
                    clean.equals("м", ignoreCase = true) ||
                    clean.equals("ж", ignoreCase = true)

            if (clean.isNotEmpty() && !isWcText) {
                val cx = tx(txt.x)
                val cy = ty(txt.y)

                textLabels.add(
                    TextLabel(
                        text = clean,
                        x = cx,
                        y = cy,
                        color = "#666666"
                    )
                )
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