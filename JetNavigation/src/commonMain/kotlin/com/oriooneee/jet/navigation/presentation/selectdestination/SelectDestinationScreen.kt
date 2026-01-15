package com.oriooneee.jet.navigation.presentation.selectdestination

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oriooneee.jet.navigation.domain.entities.graph.Node
import com.oriooneee.jet.navigation.domain.entities.graph.UniversityNavGraph
import com.oriooneee.jet.navigation.domain.entities.plan.UniversityPlan
import jetnavigation.jetnavigation.generated.resources.Res
import kotlinx.serialization.json.Json
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectDestinationScreen(
    onSelect: (Node) -> Unit,
    onBack: () -> Unit
) {
    var navigationGraph by remember { mutableStateOf(null as UniversityNavGraph?) }
    var universityPlan by remember { mutableStateOf(null as UniversityPlan?) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFloor by remember { mutableStateOf<Int?>(null) }

    val nodesByFloor = remember(navigationGraph) {
        val graph = navigationGraph ?: return@remember emptyList<Pair<Int, List<Node>>>()
        val floorZSamples = mutableMapOf<Int, MutableList<Double>>()

        graph.nodes.forEach { node ->
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

        val floorMeans = floorZSamples.mapValues { (_, zList) -> zList.average() }

        graph.nodes
            .filter { !it.id.contains("TURN") }
            .groupBy { node ->
                if (floorMeans.isNotEmpty()) {
                    floorMeans.minByOrNull { (_, meanZ) -> abs(meanZ - node.z) }?.key ?: 1
                } else 1
            }.toList().sortedBy { it.first }
    }

    val filteredNodes = remember(searchQuery, selectedFloor, nodesByFloor) {
        val allNodes = if (selectedFloor != null) {
            nodesByFloor.find { it.first == selectedFloor }?.second ?: emptyList()
        } else {
            nodesByFloor.flatMap { it.second }
        }

        allNodes.filter { it.id.contains(searchQuery, ignoreCase = true) }
            .sortedWith(
                compareBy<Node> { !it.id.contains("ENTER") }
                .thenBy { !it.id.contains("WC") }.reversed()
                .thenBy { node ->
                    if (node.id.contains("AUD")) {
                        Int.MAX_VALUE - (node.id.filter { it.isDigit() }.toIntOrNull() ?: 0)
                    } else {
                        Int.MAX_VALUE
                    }
                }
                .thenBy { it.id }
            ).reversed()
    }

    LaunchedEffect(Unit) {
        val json = Res.readBytes("files/navigation_graph.json").decodeToString()
        navigationGraph = Json.decodeFromString(json)
        val planJson = Res.readBytes("files/scene.json").decodeToString()
        universityPlan = Json.decodeFromString(planJson)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Select Destination") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.Close, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            ScrollableTabRow(
                selectedTabIndex = if (selectedFloor == null) 0 else nodesByFloor.indexOfFirst { it.first == selectedFloor } + 1,
                edgePadding = 16.dp,
                divider = {},
                indicator = {},
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                nodesByFloor.forEach { (floor, _) ->
                    InputChip(
                        selected = selectedFloor == floor,
                        onClick = {
                            selectedFloor = if (selectedFloor == floor) null else floor
                        },
                        label = { Text("Floor $floor") },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredNodes.filter {
                    !it.label.isNullOrBlank()
                }) { node ->
                    ListItem(
                        headlineContent = {
                            Text(
                                node.label ?: node.id,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelect(node)
                            }
                    )
                }
            }
        }
    }
}