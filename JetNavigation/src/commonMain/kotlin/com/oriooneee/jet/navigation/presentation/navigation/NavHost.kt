package com.oriooneee.jet.navigation.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.oriooneee.jet.navigation.presentation.NavigationScreen
import com.oriooneee.jet.navigation.presentation.NavigationViewModel
import com.oriooneee.jet.navigation.presentation.selectdestination.SelectDestinationScreen

val LocalNavController = staticCompositionLocalOf<NavController> {
    error("No NavController provided")
}

@Composable
fun NavigationApp(
    isDarkTheme: Boolean
) {
    val navController = rememberNavController()
    val vm: NavigationViewModel = remember { NavigationViewModel() }
    CompositionLocalProvider(
        LocalNavController provides navController
    ) {

            NavHost(
                navController = navController,
                startDestination = Route.NavigationScreen
            ) {
                composable<Route.NavigationScreen>(
                    exitTransition = {
                        fadeOut(animationSpec = tween(300))
                    },
                    popEnterTransition = {
                        fadeIn(animationSpec = tween(300))
                    }
                ) {
                    NavigationScreen(vm, isDarkTheme)
                }

                composable<Route.SelectDestination>(
                    enterTransition = {
                        slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(300)
                        ) + fadeIn(animationSpec = tween(300))
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(300))
                    },
                    popEnterTransition = {
                        fadeIn(animationSpec = tween(300))
                    },
                    popExitTransition = {
                        slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(300)
                        ) + fadeOut(animationSpec = tween(300))
                    }
                ) { backStackEntry ->
                    val route = backStackEntry.toRoute<Route.SelectDestination>()
                    val isStartNode = route.isStartNode

                    Surface(modifier = Modifier.fillMaxSize()) {
                        SelectDestinationScreen(
                            onBack = {
                                navController.popBackStack()
                            },
                            isSelectedStartNode = route.isSelectedStartNode,
                            isStartNode = isStartNode
                        )
                    }
                }
            }
    }
}