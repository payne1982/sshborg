package com.sshborg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sshborg.service.SessionManager
import com.sshborg.service.SshForegroundService
import com.sshborg.ui.hosts.AddEditHostScreen
import com.sshborg.ui.hosts.HostsScreen
import com.sshborg.ui.keys.KeysScreen
import com.sshborg.ui.sftp.SftpScreen
import com.sshborg.ui.terminal.TerminalScreen

sealed class Screen(val route: String) {
    object Hosts : Screen("hosts")
    object AddEditHost : Screen("hosts/{hostId}") {
        fun routeFor(id: Long) = "hosts/$id"
        const val NEW_ID = -1L
    }
    object Terminal : Screen("terminal/{sessionId}") {
        fun routeFor(sessionId: String) = "terminal/$sessionId"
    }
    object Sftp : Screen("sftp/{sessionId}") {
        fun routeFor(sessionId: String) = "sftp/$sessionId"
    }
    object Keys : Screen("keys")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as SshBorgApp
    val sessionManager = app.sessionManager

    NavHost(navController = navController, startDestination = Screen.Hosts.route) {

        composable(Screen.Hosts.route) {
            val sessions by sessionManager.sessions.collectAsState()

            HostsScreen(
                sessions = sessions,
                onNewTerminal = { hostId, hostLabel ->
                    val id = sessionManager.create(hostId, hostLabel, SessionManager.SessionType.Shell)
                    SshForegroundService.start(context)
                    navController.navigate(Screen.Terminal.routeFor(id))
                },
                onResumeTerminal = { sessionId ->
                    navController.navigate(Screen.Terminal.routeFor(sessionId))
                },
                onNewSftp = { hostId, hostLabel ->
                    val id = sessionManager.create(hostId, hostLabel, SessionManager.SessionType.Sftp)
                    SshForegroundService.start(context)
                    navController.navigate(Screen.Sftp.routeFor(id))
                },
                onResumeSftp = { sessionId ->
                    navController.navigate(Screen.Sftp.routeFor(sessionId))
                },
                onAddHost  = { navController.navigate(Screen.AddEditHost.routeFor(Screen.AddEditHost.NEW_ID)) },
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
                onBack  = { navController.popBackStack() },
            )
        }

        composable(
            Screen.Terminal.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backEntry ->
            val sessionId = backEntry.arguments?.getString("sessionId") ?: return@composable
            val sessions by sessionManager.sessions.collectAsState()

            TerminalScreen(
                sessionId = sessionId,
                sessions  = sessions,
                onBack    = { navController.popBackStack() },
                onSwitchSession = { newId ->
                    navController.navigate(Screen.Terminal.routeFor(newId)) {
                        popUpTo(Screen.Terminal.routeFor(sessionId)) { inclusive = true }
                    }
                },
            )
        }

        composable(
            Screen.Sftp.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backEntry ->
            val sessionId = backEntry.arguments?.getString("sessionId") ?: return@composable
            SftpScreen(
                sessionId = sessionId,
                onBack    = { navController.popBackStack() },
            )
        }

        composable(Screen.Keys.route) {
            KeysScreen(onBack = { navController.popBackStack() })
        }
    }
}
