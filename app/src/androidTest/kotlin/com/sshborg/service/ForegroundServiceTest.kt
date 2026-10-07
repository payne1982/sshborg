package com.sshborg.service

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sshborg.TestApp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The service that keeps the sessions alive while the app is not in front.
 *
 * Two things are checked here, and they are different in kind. The first is the declaration in the
 * manifest, which costs nothing to check and is the one mistake that cannot be caught by reading
 * the code: a foreground service type and its permission have to agree, or the app dies with a
 * SecurityException the first time somebody opens a session. The second is what happens when the
 * system takes the foreground service away on a timer — the failure that closed sessions left open
 * overnight, and the one place where the app can crash instead of stopping.
 */
@RunWith(AndroidJUnit4::class)
class ForegroundServiceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = TestApp.app
    private val packageName = context.packageName
    private val component = ComponentName(packageName, SshForegroundService::class.java.name)

    private val notifications
        get() = (context.getSystemService(NotificationManager::class.java)).activeNotifications

    /**
     * Both service tests read the service's own notifications to see what it did. With
     * POST_NOTIFICATIONS denied, notify() is dropped and the tests would fail for the wrong reason —
     * scripts/run-instrumented.sh grants it, and this says so when something else is running them.
     */
    @Before fun requireNotifications() {
        assumeTrue(
            "POST_NOTIFICATIONS is denied on this device, so the service's notifications are " +
                "dropped and there is nothing to observe: adb shell pm grant $packageName " +
                "android.permission.POST_NOTIFICATIONS",
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
        )
    }

    @After fun leaveNothingRunning() {
        app.sessionManager.removeAll()
        context.stopService(Intent(context, SshForegroundService::class.java))
        // Only where the knob exists. `device_config` arrived in Android 10, and asking a device
        // that has no such command is not a harmless no-op: the shell answers "not found" and
        // never closes the pipe, so the read below blocks for ever and the whole run hangs with
        // this test neither passing nor failing. Nothing to undo below 35 anyway — the test that
        // sets it needs that version.
        if (Build.VERSION.SDK_INT >= 35) {
            shell("device_config delete activity_manager data_sync_fgs_timeout_duration")
        }
        context.getSystemService(NotificationManager::class.java)
            .cancel(SshForegroundService.NOTIFICATION_ID_TIMEOUT)
    }

    /**
     * The manifest's service type, the permission it requires and the explanation a reviewer reads
     * have to be one consistent set. Android 14+ refuses startForeground() outright when the type
     * is declared without its permission, and `specialUse` without its subtype property is a
     * listing that gets sent back. This test is here so that going back to `dataSync` — which is
     * what happens if the review refuses `specialUse` — cannot be done by halves.
     */
    @Test fun theDeclaredServiceTypeMatchesItsPermission() {
        // The attribute arrived in Android 10: an older platform does not parse it, reports the
        // type as NONE and leaves this test nothing to check. The manifest is still right there —
        // a type it cannot read is a type it cannot refuse either.
        assumeTrue(
            "foregroundServiceType is read from the manifest only on API 29+",
            Build.VERSION.SDK_INT >= 29,
        )
        val info = context.packageManager.getServiceInfo(component, 0)
        val permissions = context.packageManager
            .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toList()

        when (val type = info.foregroundServiceType) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE -> {
                assertTrue(
                    "the service is declared specialUse but FOREGROUND_SERVICE_SPECIAL_USE is not " +
                        "requested: startForeground() throws SecurityException on Android 14+",
                    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE" in permissions,
                )
                assertFalse(
                    "FOREGROUND_SERVICE_DATA_SYNC is still requested and nothing uses it any more",
                    "android.permission.FOREGROUND_SERVICE_DATA_SYNC" in permissions,
                )
                assumeTrue("<property> is read from the manifest only on API 31+", Build.VERSION.SDK_INT >= 31)
                val subtype = context.packageManager
                    .getProperty("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE", component)
                    .string
                assertTrue(
                    "specialUse needs a PROPERTY_SPECIAL_USE_FGS_SUBTYPE explaining the case to a " +
                        "Play reviewer; it says \"$subtype\"",
                    (subtype?.length ?: 0) > 40,
                )
            }

            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC -> assertTrue(
                "the service is declared dataSync but FOREGROUND_SERVICE_DATA_SYNC is not requested",
                "android.permission.FOREGROUND_SERVICE_DATA_SYNC" in permissions,
            )

            else -> throw AssertionError(
                "unexpected foregroundServiceType $type: this test knows specialUse and dataSync, " +
                    "and a new type brings its own permission and its own time limit",
            )
        }
    }

    /** The service belongs to the sessions: it appears with the first one and goes with the last. */
    @Test fun theServiceLivesExactlyAsLongAsThereIsASession() {
        goHome()
        val id = app.sessionManager.create(hostId = 0L, hostLabel = "test", SessionManager.SessionType.Shell)
        SshForegroundService.start(context)
        waitFor("the ongoing notification to appear") { posted(SshForegroundService.NOTIFICATION_ID) }

        app.sessionManager.remove(id)
        waitFor("the ongoing notification to go") { !posted(SshForegroundService.NOTIFICATION_ID) }
    }

    /**
     * What the system does to a service type that has a time limit, and what the app does back.
     *
     * Android 15 caps `dataSync` at 6 cumulative hours in 24 — the clock runs while the app is in
     * the background and resets when the user brings it to the front, which is why this pushes the
     * app to the home screen and leaves it there. Six hours is not a test, so the limit is shortened
     * to seconds through the same device_config setting the platform documents for this, and the
     * service then has to close its sessions, say why in a notification, and stop being a foreground
     * service. If instead it holds on, the system kills the process with
     * ForegroundServiceDidNotStopInTimeException — so this test passing at all is half the assertion:
     * a crashed app takes the instrumentation with it.
     *
     * With `specialUse` in the manifest there is no limit and nothing to trigger, so the test skips
     * itself and comes back to life the day the manifest goes back to `dataSync`.
     */
    @Test fun aTimeLimitedServiceStopsCleanlyInsteadOfCrashing() {
        assumeTrue(
            "the foreground service timeout arrived in Android 15 (API 35)",
            Build.VERSION.SDK_INT >= 35,
        )
        val type = context.packageManager.getServiceInfo(component, 0).foregroundServiceType
        assumeTrue(
            "the service is declared specialUse, which has no time limit: nothing calls onTimeout()",
            type == ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        // Set before the service starts: the system reads it when it arms the timer for a service
        // that has just gone to the background, not while the timer is running.
        shell("device_config put activity_manager data_sync_fgs_timeout_duration $SHORT_LIMIT_MS")
        assertEquals(
            "the shortened dataSync limit did not take; without it this test would wait 6 hours",
            SHORT_LIMIT_MS.toString(),
            shell("device_config get activity_manager data_sync_fgs_timeout_duration").trim(),
        )

        goHome()
        app.sessionManager.create(hostId = 0L, hostLabel = "test", SessionManager.SessionType.Shell)
        SshForegroundService.start(context)
        waitFor("the ongoing notification to appear") { posted(SshForegroundService.NOTIFICATION_ID) }

        waitFor("the system to spend the service's time limit", timeoutMs = SHORT_LIMIT_MS + 120_000) {
            posted(SshForegroundService.NOTIFICATION_ID_TIMEOUT)
        }
        assertTrue(
            "the sessions are still open after the service lost its foreground state: they are " +
                "connections nobody is keeping alive any more, and the user was told they stopped",
            app.sessionManager.sessions.value.isEmpty(),
        )
        assertFalse(
            "the ongoing notification is still up after the service was told to stop",
            posted(SshForegroundService.NOTIFICATION_ID),
        )
    }

    /**
     * The app to the background, which is where a session is when this matters — and the only place
     * the system's clock for it runs at all.
     */
    private fun goHome() {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        Thread.sleep(2_000)
    }

    private fun posted(id: Int) = notifications.any { it.id == id }

    private fun waitFor(what: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(500)
        }
        throw AssertionError("waited ${timeoutMs / 1000}s for $what")
    }

    /**
     * Runs as the shell user, which is what a test needs for the platform's own knobs.
     *
     * Never hand it a command the device may not have: a missing one leaves the pipe open and this
     * read never returns. Guard by version at the call instead.
     */
    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes().decodeToString() }

    private companion object {
        /** Long enough that the service is properly up first, short enough to be a test. */
        const val SHORT_LIMIT_MS = 30_000L
    }
}
