package com.sshborg

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.sshborg.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import com.sshborg.ui.settings.SettingsScreen
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
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as SshBorgApp
    val sessionManager = app.sessionManager

    // Privacy policy — must be accepted on first launch
    var showPrivacyDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(Unit) {
        if (!app.appPreferences.privacyPolicyAccepted.first()) showPrivacyDialog = true
    }
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.privacy_policy_dialog_title)) },
            text  = { Text(stringResource(R.string.privacy_policy_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showPrivacyDialog = false
                    scope.launch { app.appPreferences.setPrivacyPolicyAccepted() }
                }) { Text(stringResource(R.string.action_accept)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    uriHandler.openUri("https://sshborg.com/privacy_policy.html")
                }) { Text(stringResource(R.string.action_read_policy)) }
            },
        )
    }

    // One-time root warning
    val isRooted = remember { RootDetector.isRooted() }
    var showRootWarning by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (isRooted && !app.appPreferences.rootWarningAcknowledged.first()) {
            showRootWarning = true
        }
    }
    if (showRootWarning) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.root_warning_title)) },
            text  = { Text(stringResource(R.string.root_warning_body)) },
            confirmButton = {
                OutlinedButton(onClick = {
                    showRootWarning = false
                    scope.launch { app.appPreferences.setRootWarningAcknowledged() }
                }) { Text(stringResource(R.string.action_i_understand)) }
            },
        )
    }

    // Delayed security reminder (shown if biometric or keystore encryption is not enabled)
    // Key on showPrivacyDialog so the reminder waits until privacy policy is accepted
    var showSecurityReminder by remember { mutableStateOf(false) }
    LaunchedEffect(showPrivacyDialog) {
        if (showPrivacyDialog) return@LaunchedEffect
        delay(1500L)
        val dismissed  = app.appPreferences.securityReminderDismissed.first()
        if (!dismissed) {
            val biometric = app.appPreferences.biometricLock.first()
            val keystore  = app.appPreferences.keystoreEncryption.first()
            if (!biometric || !keystore) showSecurityReminder = true
        }
    }
    if (showSecurityReminder) {
        AlertDialog(
            onDismissRequest = { showSecurityReminder = false },
            title = { Text(stringResource(R.string.security_reminder_title)) },
            text  = { Text(stringResource(R.string.security_reminder_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showSecurityReminder = false
                    scope.launch { app.appPreferences.setSecurityReminderDismissed() }
                }) { Text(stringResource(R.string.action_dont_show_again)) }
            },
            dismissButton = {
                TextButton(onClick = { showSecurityReminder = false }) {
                    Text(stringResource(R.string.action_remind_later))
                }
            },
        )
    }

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
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
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

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
