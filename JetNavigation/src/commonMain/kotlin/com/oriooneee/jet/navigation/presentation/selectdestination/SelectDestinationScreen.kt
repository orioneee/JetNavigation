package com.oriooneee.jet.navigation.presentation.selectdestination

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Class
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Man
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Wc
import androidx.compose.material.icons.outlined.Woman
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oriooneee.jet.navigation.domain.entities.graph.Node
import com.oriooneee.jet.navigation.domain.entities.graph.NodeType
import com.oriooneee.jet.navigation.domain.entities.graph.SelectNodeResult
import com.oriooneee.jet.navigation.domain.entities.graph.UniversityNavGraph
import com.oriooneee.jet.navigation.presentation.KEY_SELECTED_END_NODE
import com.oriooneee.jet.navigation.presentation.KEY_SELECTED_START_NODE
import com.oriooneee.jet.navigation.presentation.navigation.LocalNavController
import jetnavigation.jetnavigation.generated.resources.Res
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectDestinationScreen(
    isStartNode: Boolean,
    isSelectedStartNode: Boolean,
    onBack: () -> Unit
) {
    val navController = LocalNavController.current
    var navigationGraph by remember { mutableStateOf(null as UniversityNavGraph?) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFloor by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        val json = Res.readBytes("files/navigation_graph.json").decodeToString()
        navigationGraph = Json.decodeFromString(json)
    }

    fun handleSelection(result: SelectNodeResult) {
        val key = if (isStartNode) KEY_SELECTED_START_NODE else KEY_SELECTED_END_NODE
        val jsonResult = Json.encodeToString(result)
        navController.previousBackStackEntry?.savedStateHandle?.set(key, jsonResult)
        navController.popBackStack()
    }

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

        allNodes.filter {
            !it.label.isNullOrBlank() &&
                    (it.label.contains(searchQuery, ignoreCase = true) || it.id.contains(searchQuery, ignoreCase = true))
        }
            .sortedWith(
                compareBy<Node> { node ->
                    when {
                        node.type == NodeType.MAIN_ENTRANCE -> 0
                        node.id.contains("WC", ignoreCase = true) -> 1
                        node.type == NodeType.AUDITORIUM -> 2
                        node.type == NodeType.TRANSFER_TO_ANOTHER_BUILDING -> 3
                        else -> 4
                    }
                }.thenBy { node ->
                    if (node.type == NodeType.AUDITORIUM) {
                        node.id.filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
                    } else {
                        0
                    }
                }.thenBy { it.label ?: it.id }
            )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (isStartNode) "Start Point" else "Destination",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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
                placeholder = { Text("Search room, hall, etc.") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                } else null,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                ),
                singleLine = true
            )

            if (nodesByFloor.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = if (selectedFloor == null) 0 else nodesByFloor.indexOfFirst { it.first == selectedFloor } + 1,
                    edgePadding = 16.dp,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = {},
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    nodesByFloor.forEach { (floor, _) ->
                        InputChip(
                            selected = selectedFloor == floor,
                            onClick = {
                                selectedFloor = if (selectedFloor == floor) null else floor
                            },
                            label = { Text("$floor Floor") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {

                if (!isStartNode && isSelectedStartNode && searchQuery.isEmpty() && selectedFloor == null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        QuickActionRow(
                            onManWcClick = { handleSelection(SelectNodeResult.NearestManWC) },
                            onWomanWcClick = { handleSelection(SelectNodeResult.NearestWomanWC) },
                            onExitClick = { handleSelection(SelectNodeResult.NearestMainEntrance) }
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(8.dp))
                    }
                }

                items(
                    filteredNodes,
                    key = { it.id }
                ) { node ->
                    NodeListItem(node = node) {
                        handleSelection(SelectNodeResult.SelectedNode(node))
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionRow(
    onManWcClick: () -> Unit,
    onWomanWcClick: () -> Unit,
    onExitClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionButton(
            icon = Icons.Outlined.Man,
            label = "WC",
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
            onClick = onManWcClick
        )
        QuickActionButton(
            icon = Icons.Outlined.Woman,
            label = "WC",
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
            onClick = onWomanWcClick
        )
        QuickActionButton(
            icon = Icons.Outlined.DoorFront,
            label = "Exit",
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
            onClick = onExitClick
        )
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

@Composable
fun NodeListItem(node: Node, onClick: () -> Unit) {
    val (icon, color) = when {
        node.type == NodeType.MAIN_ENTRANCE -> Icons.Outlined.DoorFront to MaterialTheme.colorScheme.primary
        node.id.contains("WC", ignoreCase = true) -> Icons.Outlined.Wc to MaterialTheme.colorScheme.tertiary
        node.type == NodeType.AUDITORIUM -> Icons.Outlined.Class to MaterialTheme.colorScheme.secondary
        node.type == NodeType.TRANSFER_TO_ANOTHER_BUILDING -> Icons.Outlined.DirectionsWalk to MaterialTheme.colorScheme.primary
        else -> Icons.Outlined.Place to MaterialTheme.colorScheme.onSurfaceVariant
    }

    ListItem(
        headlineContent = {
            Text(
                text = node.label ?: node.id,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    )
}