package com.sshborg

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sshborg.ui.hosts.AddEditHostScreen
import com.sshborg.ui.hosts.HostsScreen
import com.sshborg.ui.keys.KeysScreen
import com.sshborg.ui.terminal.TerminalScreen

sealed class Screen(val route: String) {
    object Hosts : Screen("hosts")
    object AddEditHost : Screen("hosts/{hostId}") {
        fun routeFor(id: Long) = "hosts/$id"
        const val NEW_ID = -1L
    }
    object Terminal : Screen("terminal/{hostId}") {
        fun routeFor(id: Long) = "terminal/$id"
    }
    object Keys : Screen("keys")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Hosts.route) {

        composable(Screen.Hosts.route) {
            HostsScreen(
                onHostClick = { hostId -> navController.navigate(Screen.Terminal.routeFor(hostId)) },
                onAddHost = { navController.navigate(Screen.AddEditHost.routeFor(Screen.AddEditHost.NEW_ID)) },
                onEditHost = { hostId -> navController.navigate(Screen.AddEditHost.routeFor(hostId)) },
                onKeysClick = { navController.navigate(Screen.Keys.route) },
            )
        }

        composable(
            Screen.AddEditHost.route,
            arguments = listOf(navArgument("hostId") { type = NavType.LongType }),
        ) { backEntry ->
            val hostId = backEntry.arguments?.getLong("hostId") ?: Screen.AddEditHost.NEW_ID
            AddEditHostScreen(
                hostId = hostId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Screen.Terminal.route,
            arguments = listOf(navArgument("hostId") { type = NavType.LongType }),
        ) { backEntry ->
            val hostId = backEntry.arguments?.getLong("hostId") ?: return@composable
            TerminalScreen(
                hostId = hostId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Keys.route) {
            KeysScreen(onBack = { navController.popBackStack() })
        }
    }
}
