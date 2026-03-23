package com.sshborg

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.sshborg.ui.theme.SshBorgTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        val prefs = (application as SshBorgApp).appPreferences
        setContent {
            val nightMode by prefs.nightMode.collectAsState(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            SshBorgTheme(nightMode = nightMode) {
                AppNavigation()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val app = application as SshBorgApp
        lifecycleScope.launch {
            val biometricEnabled = app.appPreferences.biometricLock.first()
            if (!biometricEnabled) return@launch
            val timeoutMs = app.appPreferences.lockTimeoutSeconds.first() * 1_000L
            val elapsed = System.currentTimeMillis() - app.lastAuthTime
            if (elapsed <= timeoutMs) return@launch
            val ok = BiometricHelper.authenticate(this@MainActivity)
            if (ok) app.lastAuthTime = System.currentTimeMillis()
            else finish()
        }
    }
}
