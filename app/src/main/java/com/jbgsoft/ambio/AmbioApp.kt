package com.jbgsoft.ambio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jbgsoft.ambio.feature.home.HomeScreen
import com.jbgsoft.ambio.feature.settings.SettingsScreen
import com.jbgsoft.ambio.feature.stats.StatsScreen
import com.jbgsoft.ambio.ui.theme.AmbioTheme

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val STATS = "stats"
}

@Composable
fun AmbioApp(viewModel: AmbioAppViewModel = hiltViewModel()) {
    val soundTheme by viewModel.soundTheme.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    // The theme wraps the whole graph, not just Home, so Settings and Stats
    // inherit the palette of the selected sound.
    AmbioTheme(soundTheme = soundTheme) {
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onNavigateToStats = { navController.navigate(Routes.STATS) }
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
