package com.realworldmagic.parkpin.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.realworldmagic.parkpin.ui.ar.ArGuideScreen
import com.realworldmagic.parkpin.ui.guide.GuideScreen
import com.realworldmagic.parkpin.ui.history.HistoryScreen
import com.realworldmagic.parkpin.ui.home.HomeScreen
import com.realworldmagic.parkpin.ui.map.MapScreen
import com.realworldmagic.parkpin.ui.settings.SettingsScreen
import com.realworldmagic.parkpin.ui.spot.SpotEditScreen

/** Route strings from ARCHITECTURE.md. A spotId of -1 means "the active spot". */
object Routes {
    const val HOME = "home"
    const val GUIDE = "guide/{spotId}"
    const val AR = "ar/{spotId}"
    const val MAP = "map/{spotId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val SPOT_EDIT = "spotEdit/{spotId}"
    const val ARG_SPOT_ID = "spotId"
    const val ACTIVE_SPOT = -1L

    fun guide(id: Long) = "guide/$id"
    fun ar(id: Long) = "ar/$id"
    fun map(id: Long = ACTIVE_SPOT) = "map/$id"
    fun spotEdit(id: Long) = "spotEdit/$id"
}

private data class TopLevel(val route: String, val pattern: String, val label: String, val icon: ImageVector)

private val topLevels = listOf(
    TopLevel(Routes.HOME, Routes.HOME, "Home", Icons.Filled.Home),
    TopLevel(Routes.map(), Routes.MAP, "Map", Icons.Filled.Map),
    TopLevel(Routes.HISTORY, Routes.HISTORY, "History", Icons.Filled.History),
    TopLevel(Routes.SETTINGS, Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun ParkPinNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    pendingGuideSpotId: Long? = null,
    onPendingConsumed: () -> Unit = {},
) {
    LaunchedEffect(pendingGuideSpotId) {
        if (pendingGuideSpotId != null && pendingGuideSpotId > 0) {
            navController.navigate(Routes.guide(pendingGuideSpotId)) { launchSingleTop = true }
            onPendingConsumed()
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentPattern = backStack?.destination?.route
    val showBottomBar = topLevels.any { it.pattern == currentPattern }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevels.forEach { item ->
                        NavigationBarItem(
                            selected = currentPattern == item.pattern,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val spotArg = listOf(navArgument(Routes.ARG_SPOT_ID) { type = NavType.LongType })
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onGuide = { navController.navigate(Routes.guide(it)) },
                    onAr = { navController.navigate(Routes.ar(it)) },
                    onMap = { navController.navigate(Routes.map(it)) },
                    onEdit = { navController.navigate(Routes.spotEdit(it)) },
                )
            }
            composable(Routes.GUIDE, arguments = spotArg) { entry ->
                val id = entry.arguments?.getLong(Routes.ARG_SPOT_ID) ?: Routes.ACTIVE_SPOT
                GuideScreen(
                    spotId = id,
                    onBack = { navController.popBackStack() },
                    onOpenAr = { navController.navigate(Routes.ar(it)) },
                    onOpenMap = { navController.navigate(Routes.map(it)) },
                    onFoundCar = {
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    },
                )
            }
            composable(Routes.AR, arguments = spotArg) { entry ->
                val id = entry.arguments?.getLong(Routes.ARG_SPOT_ID) ?: Routes.ACTIVE_SPOT
                ArGuideScreen(spotId = id, onBack = { navController.popBackStack() })
            }
            composable(Routes.MAP, arguments = spotArg) { entry ->
                val id = entry.arguments?.getLong(Routes.ARG_SPOT_ID) ?: Routes.ACTIVE_SPOT
                MapScreen(
                    spotId = id,
                    onGuide = { navController.navigate(Routes.guide(it)) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onGuide = { navController.navigate(Routes.guide(it)) })
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.SPOT_EDIT, arguments = spotArg) { entry ->
                val id = entry.arguments?.getLong(Routes.ARG_SPOT_ID) ?: Routes.ACTIVE_SPOT
                SpotEditScreen(spotId = id, onBack = { navController.popBackStack() })
            }
        }
    }
}
