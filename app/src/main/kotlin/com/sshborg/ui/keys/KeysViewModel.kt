package com.sshborg.ui.keys

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sshborg.SshBorgApp
import com.sshborg.data.KeystoreManager
import com.sshborg.data.db.SshKeyEntity
import com.sshborg.data.ssh.SshManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KeysViewModel(app: Application) : AndroidViewModel(app) {

    private val sshBorgApp = app as SshBorgApp
    private val dao        = sshBorgApp.db.sshKeyDao()
    private val prefs      = sshBorgApp.appPreferences

    val keys: StateFlow<List<SshKeyEntity>> =
        dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun generateKey(label: String, type: String, size: String = "", onDone: () -> Unit) = viewModelScope.launch {
        val (priv, pub) = SshManager.generateKeyPair(type, comment = label, bits = size.toIntOrNull())
        val encEnabled = prefs.keystoreEncryption.first()
        val entity = if (encEnabled) {
            val blob = withContext(Dispatchers.IO) { KeystoreManager.encrypt(priv) }
            SshKeyEntity(label = label, keyType = type, privateKeyPem = "", encryptedBlob = blob, publicKey = pub)
        } else {
            SshKeyEntity(label = label, keyType = type, privateKeyPem = priv, publicKey = pub)
        }
        dao.upsert(entity)
        onDone()
    }

    fun deleteKey(key: SshKeyEntity) = viewModelScope.launch { dao.delete(key) }
}
