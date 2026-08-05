package com.jbgsoft.ambio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jbgsoft.ambio.feature.home.HomeScreen
import com.jbgsoft.ambio.feature.settings.SettingsScreen
import com.jbgsoft.ambio.feature.stats.StatsScreen
import com.jbgsoft.ambio.feature.widget.WidgetUpdater
import com.jbgsoft.ambio.ui.theme.AmbioTheme

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val STATS = "stats"
}

@Composable
fun AmbioApp(viewModel: AmbioAppViewModel = hiltViewModel()) {
    val palette by viewModel.palette.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    // The app is the only thing that knows the mix changed: the mix is written by this
    // UI, and AudioService never sees an edit made while it is dead.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.activeMix.collect { WidgetUpdater.refresh(context) }
    }

    // The theme wraps the whole graph, not just Home, so Settings and Stats
    // inherit the palette of the active mix.
    AmbioTheme(palette = palette) {
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToSettings = {
                        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                    },
                    onNavigateToStats = {
                        navController.navigate(Routes.STATS) { launchSingleTop = true }
                    }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Routes.STATS) {
                StatsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
