package io.github.timilehin_dev.ogabot.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.timilehin_dev.ogabot.AppGraph

/** Typed routes — see docs/APP_DESIGN.md §4. */
object Routes {
    const val BOTS = "bots"
    const val ACTIVITY = "activity"
    const val SETTINGS = "settings"
    const val SETUP = "setup"
    const val CHAT = "chat/{botId}"
    const val BOT = "bot/{botId}"
    fun bot(botId: String) = "bot/$botId"
    fun chat(botId: String) = "chat/$botId"
}

@Composable
fun AppRoot(graph: AppGraph) {
    var providerConfigured by remember { mutableStateOf(graph.vault.load() != null) }

    if (!providerConfigured) {
        SetupScreen(graph, isOnboarding = true) { providerConfigured = true }
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onChat = currentDestination?.hierarchy?.any { it.route == Routes.CHAT } == true

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            if (!onChat) {
                NavigationBar(containerColor = SurfaceLow) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.SmartToy, contentDescription = "Bots") },
                        label = { Text("Bots") },
                        selected = currentDestination?.hierarchy?.any { it.route == Routes.BOTS } == true,
                        onClick = {
                            navController.navigate(Routes.BOTS) {
                                launchSingleTop = true
                                popUpTo(Routes.BOTS) { inclusive = true }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Notifications, contentDescription = "Activity") },
                        label = { Text("Activity") },
                        selected = currentDestination?.hierarchy?.any { it.route == Routes.ACTIVITY } == true,
                        onClick = {
                            navController.navigate(Routes.ACTIVITY) {
                                launchSingleTop = true
                                popUpTo(Routes.BOTS)
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = currentDestination?.hierarchy?.any { it.route == Routes.SETTINGS } == true,
                        onClick = {
                            navController.navigate(Routes.SETTINGS) {
                                launchSingleTop = true
                                popUpTo(Routes.BOTS)
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.BOTS,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.BOTS) {
                BotsScreen(graph) { botId -> navController.navigate(Routes.bot(botId)) }
            }
            composable(Routes.ACTIVITY) { ActivityScreen(graph) }
            composable(Routes.SETTINGS) { SettingsScreen(graph) }
            composable(Routes.SETUP) { SetupScreen(graph, isOnboarding = false) { navController.popBackStack() } }
            composable(
                Routes.BOT,
                arguments = listOf(navArgument("botId") { type = NavType.StringType })
            ) { entry ->
                val botId = entry.arguments?.getString("botId").orEmpty()
                BotDetailScreen(graph, botId) { navController.popBackStack() }
            }
        }
    }
}
