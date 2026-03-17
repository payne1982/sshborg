package com.sshborg.ui.keys

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sshborg.SshBorgApp
import com.sshborg.data.db.SshKeyEntity
import com.sshborg.data.ssh.SshManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class KeysViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = (app as SshBorgApp).db.sshKeyDao()

    val keys: StateFlow<List<SshKeyEntity>> =
        dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun generateKey(label: String, type: String, size: String = "", onDone: () -> Unit) = viewModelScope.launch {
        val (priv, pub) = SshManager.generateKeyPair(type, comment = label, bits = size.toIntOrNull())
        dao.upsert(SshKeyEntity(label = label, keyType = type, privateKeyPem = priv, publicKey = pub))
        onDone()
    }

    fun deleteKey(key: SshKeyEntity) = viewModelScope.launch { dao.delete(key) }
}
