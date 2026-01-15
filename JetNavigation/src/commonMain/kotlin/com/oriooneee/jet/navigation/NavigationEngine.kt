package com.oriooneee.jet.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.oriooneee.jet.navigation.domain.entities.NavigationDirection
import com.oriooneee.jet.navigation.domain.entities.NavigationStep
import com.oriooneee.jet.navigation.domain.entities.graph.Node
import com.oriooneee.jet.navigation.domain.entities.graph.UniversityNavGraph
import com.oriooneee.jet.navigation.domain.entities.plan.Flor
import com.oriooneee.jet.navigation.domain.entities.plan.UniversityPlan
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
data class TextLabel(
    val text: String,
    val x: Float,
    val y: Float,
    val fontSize: Float,
    val color: String = "#000000",
    val bold: Boolean = false,
    val hasBackground: Boolean = false
)

class NavigationEngine(
    private val navGraph: UniversityNavGraph,
    private val plan: UniversityPlan,
) {
    private val outputWidth = 2000.0
    private val paddingPct = 0.05
    private val drawStairs = false
    private val zToFloor = mutableMapOf<Double, Int>()

    init {
        val floorZSamples = mutableMapOf<Int, MutableList<Double>>()

        navGraph.nodes.forEach { node ->
            if (node.id.contains("_FLOR_")) {
                val parts = node.id.split("_FLOR_")
                if (parts.size > 1) {
                    val floorStr = parts[1].split("_")[0]
                    val floorNum = floorStr.toIntOrNull()
                    if (floorNum != null) {
                        floorZSamples.getOrPut(floorNum) { mutableListOf() }.add(node.z)
                    }
                }
            }
        }

        val floorMeans = floorZSamples.mapValues { (_, zList) ->
            zList.average()
        }

        navGraph.nodes.forEach { node ->
            if (floorMeans.isNotEmpty()) {
                val closestFloor = floorMeans.minByOrNull { (_, meanZ) ->
                    abs(meanZ - node.z)
                }?.key ?: 1
                zToFloor[node.z] = closestFloor
            } else {
                zToFloor[node.z] = 1
            }
        }
    }

    fun getRoute(
        from: Node,
        to: Node,
        planColor: Color,
        directionColor: Color,
        startNodeColor: Color,
        endNodeColor: Color,
    ): NavigationDirection {
        val path = findPath(from, to) ?: return NavigationDirection(emptyList(), 0.0)
        val totalDistance = calculateTotalDistance(path)
        val steps = buildNavigationSteps(path, planColor, directionColor, startNodeColor, endNodeColor)
        return NavigationDirection(steps, totalDistance)
    }

    private fun findPath(start: Node, end: Node): List<Node>? {
        val adjacency = mutableMapOf<String, MutableList<Pair<String, Double>>>()
        navGraph.edges.forEach { edge ->
            adjacency.getOrPut(edge.from) { mutableListOf() }.add(edge.to to edge.weight)
        }

        val distances = mutableMapOf<String, Double>()
        val previous = mutableMapOf<String, String>()
        val nodesMap = navGraph.nodes.associateBy { it.id }

        navGraph.nodes.forEach { distances[it.id] = Double.MAX_VALUE }
        distances[start.id] = 0.0

        val pq = MinHeap<Pair<String, Double>> { a, b -> a.second.compareTo(b.second) }
        pq.offer(start.id to 0.0)

        val startClean = start.id.filter { it.isLetterOrDigit() }
        val endClean = end.id.filter { it.isLetterOrDigit() }

        while (pq.isNotEmpty()) {
            val (u, d) = pq.poll() ?: break

            if (d > (distances[u] ?: Double.MAX_VALUE)) continue
            if (u == end.id) break

            adjacency[u]?.forEach { (v, weight) ->
                val node = nodesMap[v]
                if (node != null) {
                    val shouldSkip = if (node.id.contains("AUD")) {
                        val nodeClean = node.id.filter { it.isLetterOrDigit() }
                        node.id != start.id &&
                                node.id != end.id &&
                                nodeClean != startClean &&
                                nodeClean != endClean
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

        if (distances[end.id] == Double.MAX_VALUE) return null

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
            val edge = navGraph.edges.find { it.from == u.id && it.to == v.id }
            distance += edge?.weight ?: 0.0
        }
        return distance
    }

    private fun buildNavigationSteps(
        fullPath: List<Node>,
        planColor: Color,
        directionColor: Color,
        startNodeColor: Color,
        endNodeColor: Color
    ): List<NavigationStep> {
        val steps = mutableListOf<NavigationStep>()
        if (fullPath.isEmpty()) return steps

        val globalStartNode = fullPath.first()
        val globalEndNode = fullPath.last()

        val floorGroups = groupPathByFloor(fullPath)

        val nonEmptyFloorGroups = floorGroups.filter { (_, stepNodes) ->
            stepNodes.isNotEmpty()
        }

        nonEmptyFloorGroups.forEachIndexed { index, (floorNum, stepNodes) ->
            if (index > 0) {
                val previousFloor = nonEmptyFloorGroups[index - 1].first
                steps.add(
                    NavigationStep.TransitionToFlor(
                        to = floorNum,
                        from = previousFloor
                    )
                )
            }

            val florData = when (floorNum) {
                1 -> plan.flor1
                2 -> plan.flor2
                3 -> plan.flor3
                4 -> plan.flor4
                else -> plan.flor1
            }

            val (svgBytes, pointOfInterest, textLabels) = generateSvg(
                floorNum = floorNum,
                flor = florData,
                stepPath = stepNodes,
                globalStart = globalStartNode,
                globalEnd = globalEndNode,
                planColor = planColor,
                dirColor = directionColor,
                startColor = startNodeColor,
                endColor = endNodeColor
            )

            steps.add(NavigationStep.ByFlor(floorNum, svgBytes, pointOfInterest, textLabels))
        }

        return steps
    }

    private fun groupPathByFloor(path: List<Node>): List<Pair<Int, List<Node>>> {
        val groups = mutableListOf<Pair<Int, List<Node>>>()
        if (path.isEmpty()) return groups

        var currentNodes = mutableListOf<Node>()
        var currentFloor = getFloorByZ(path.first().z)

        path.forEach { node ->
            val nodeFloor = getFloorByZ(node.z)

            if (nodeFloor != currentFloor) {
                if (currentNodes.isNotEmpty()) {
                    groups.add(currentFloor to ArrayList(currentNodes))
                }
                currentNodes = mutableListOf()
                currentFloor = nodeFloor
            }
            currentNodes.add(node)
        }

        if (currentNodes.isNotEmpty()) {
            groups.add(currentFloor to currentNodes)
        }
        return groups
    }

    private fun getFloorByZ(z: Double): Int {
        return zToFloor[z] ?: 1
    }

    private fun generateSvg(
        floorNum: Int,
        flor: Flor,
        stepPath: List<Node>,
        globalStart: Node,
        globalEnd: Node,
        planColor: Color,
        dirColor: Color,
        startColor: Color,
        endColor: Color
    ): Triple<ByteArray, Offset, List<TextLabel>> {
        val allX = mutableListOf<Double>()
        val allY = mutableListOf<Double>()

        flor.polylines.forEach { p -> p.points.forEach { pt -> allX.add(pt[0]); allY.add(pt[1]) } }
        flor.lines.forEach { l -> allX.add(l.x1); allX.add(l.x2); allY.add(l.y1); allY.add(l.y2) }
        flor.texts.forEach { t -> allX.add(t.x); allY.add(t.y) }

        if (allX.isEmpty()) return Triple(ByteArray(0), Offset.Zero, emptyList())

        val minX = allX.minOrNull() ?: 0.0
        val maxX = allX.maxOrNull() ?: 1.0
        val minY = allY.minOrNull() ?: 0.0
        val maxY = allY.maxOrNull() ?: 1.0

        val dataW = maxX - minX
        val dataH = maxY - minY

        if (dataW == 0.0 || dataH == 0.0) return Triple(ByteArray(0), Offset.Zero, emptyList())

        val drawWidth = outputWidth * (1 - paddingPct * 2)
        val scale = drawWidth / dataW
        val outputHeight = (dataH * scale + (outputWidth * paddingPct * 2)).toInt()
        val padding = outputWidth * paddingPct

        fun txVal(x: Double): Double = (x - minX) * scale + padding
        fun tyVal(y: Double): Double = outputHeight - ((y - minY) * scale + padding)

        fun tx(x: Double): String = txVal(x).round(1)
        fun ty(y: Double): String = tyVal(y).round(1)

        val startStepNode = stepPath.firstOrNull()
        val pointOfInterest = if (startStepNode != null) {
            Offset(
                x = txVal(startStepNode.x).toFloat(),
                y = tyVal(startStepNode.y).toFloat()
            )
        } else {
            Offset.Zero
        }

        val stroke = max(1.0, outputHeight * 0.001)

        val planHex = colorToHex(planColor)
        val dirHex = colorToHex(dirColor)
        val startHex = colorToHex(startColor)
        val endHex = colorToHex(endColor)

        val textLabels = mutableListOf<TextLabel>()

        val sb = StringBuilder()
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${outputWidth.toInt()}\" height=\"$outputHeight\" viewBox=\"0 0 ${outputWidth.toInt()} $outputHeight\">")

        sb.append("<g id=\"geometry\">")
        flor.polylines.forEach { poly ->
            val ptsStr = poly.points.joinToString(" ") { "${tx(it[0])},${ty(it[1])}" }
            if (poly.closed) {
                sb.append("<polygon points=\"$ptsStr\" fill=\"$planHex\" fill-opacity=\"0.05\" stroke=\"$planHex\" stroke-width=\"$stroke\" />")
            } else {
                sb.append("<polyline points=\"$ptsStr\" fill=\"none\" stroke=\"$planHex\" stroke-width=\"$stroke\" />")
            }
        }
        flor.lines.forEach { l ->
            sb.append("<line x1=\"${tx(l.x1)}\" y1=\"${ty(l.y1)}\" x2=\"${tx(l.x2)}\" y2=\"${ty(l.y2)}\" stroke=\"$planHex\" stroke-width=\"$stroke\" />")
        }
        sb.append("</g>")

        sb.append("<g id=\"route\">")
        for (i in 0 until stepPath.size - 1) {
            val u = stepPath[i]
            val v = stepPath[i + 1]
            if (!drawStairs && (u.id.contains("STAIRS") || v.id.contains("STAIRS"))) continue

            sb.append("<line x1=\"${tx(u.x)}\" y1=\"${ty(u.y)}\" x2=\"${tx(v.x)}\" y2=\"${ty(v.y)}\" stroke=\"$dirHex\" stroke-width=\"${stroke * 4}\" stroke-linecap=\"round\" opacity=\"0.8\"/>")
        }
        sb.append("</g>")

        sb.append("<g id=\"markers\">")
        if (stepPath.isNotEmpty() && stepPath.first().id == globalStart.id) {
            val startNode = stepPath.first()
            sb.append("<circle cx=\"${tx(startNode.x)}\" cy=\"${ty(startNode.y)}\" r=\"${stroke * 3}\" fill=\"$startHex\" stroke=\"none\" />")
        }

        if (stepPath.isNotEmpty() && stepPath.last().id == globalEnd.id) {
            val endNode = stepPath.last()
            sb.append("<circle cx=\"${tx(endNode.x)}\" cy=\"${ty(endNode.y)}\" r=\"${stroke * 3}\" fill=\"$endHex\" stroke=\"none\" />")
        }
        sb.append("</g>")

        flor.texts.forEach { txt ->
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

            if (clean.isNotEmpty()) {
                val fontSize = (stroke * 6).toFloat()
                textLabels.add(
                    TextLabel(
                        text = clean,
                        x = txVal(txt.x).toFloat(),
                        y = tyVal(txt.y).toFloat(),
                        fontSize = fontSize,
                        color = "#666666",
                        bold = false,
                        hasBackground = false
                    )
                )
            }
        }

        sb.append("</svg>")
        return Triple(sb.toString().encodeToByteArray(), pointOfInterest, textLabels)
    }

    private fun Double.round(decimals: Int): String {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        val rounded = (this * multiplier).roundToInt() / multiplier
        return rounded.toString()
    }

    private fun colorToHex(color: Color): String {
        val argb = color.toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF

        fun byteToHex(byte: Int): String {
            val hex = byte.toString(16).uppercase()
            return if (hex.length == 1) "0$hex" else hex
        }

        return "#${byteToHex(r)}${byteToHex(g)}${byteToHex(b)}"
    }
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