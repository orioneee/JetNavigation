package com.oriooneee.jet.navigation.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oriooneee.jet.navigation.domain.entities.NavigationStep

@Composable
expect fun MapComponent(
    modifier: Modifier = Modifier,
    step: NavigationStep.OutDoorMaps,
    isDarkTheme: Boolean
)

@Composable
fun MapPlaceholderContent(
    step: NavigationStep.OutDoorMaps
){
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF81C784).copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Park,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color(0xFF4CAF50)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Outdoor Navigation",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF2E7D32)
            )
            Text(
                "${step.path.size} waypoints",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}