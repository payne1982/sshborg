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
        val (priv, pub) = SshManager.generateKeyPair(type, comment = commentFor(label), bits = size.toIntOrNull())
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

    fun importKey(
        label: String,
        pem: String,
        passphrase: String?,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ) = viewModelScope.launch {
        val typed = passphrase.takeIf { !it.isNullOrEmpty() }
        val result = runCatching {
            withContext(Dispatchers.IO) { SshManager.importPrivateKey(pem, typed) }
        }
        val imported = result.getOrElse { onError(it.message ?: ""); return@launch }
        val pubKey = withComment(imported.publicKey, commentFor(label))   // comment matches the chosen label
        // The key comes back unlocked, so there is no passphrase to keep: what the user typed
        // was needed to open it and is dropped here with the rest of this call's locals.
        val encEnabled = prefs.keystoreEncryption.first()
        val entity = withContext(Dispatchers.IO) {
            if (encEnabled) {
                SshKeyEntity(
                    label = label,
                    keyType = imported.keyType,
                    privateKeyPem = "",
                    encryptedBlob = KeystoreManager.encrypt(imported.pem),
                    publicKey = pubKey,
                )
            } else {
                SshKeyEntity(
                    label = label,
                    keyType = imported.keyType,
                    privateKeyPem = imported.pem,
                    publicKey = pubKey,
                )
            }
        }
        dao.upsert(entity)
        onDone()
    }

    /**
     * Whether [key] is stored still encrypted, which no import produces any more but every
     * encrypted key imported by a released version does — those were kept as they arrived and
     * their passphrase was thrown away, so they can never authenticate. Reading and parsing the
     * key is cheap next to a connection, and this is what the list warns about: such a key has
     * to be imported again.
     */
    suspend fun needsReimport(key: SshKeyEntity): Boolean = withContext(Dispatchers.IO) {
        val pem = KeystoreManager.getPrivateKeyPem(key) ?: return@withContext false
        SshManager.isKeyEncrypted(pem)
    }

    fun renameKey(key: SshKeyEntity, newLabel: String) = viewModelScope.launch {
        // Also refresh the comment embedded in the public key so it matches the new name.
        dao.upsert(key.copy(label = newLabel, publicKey = withComment(key.publicKey, commentFor(newLabel))))
    }

    fun deleteKey(key: SshKeyEntity) = viewModelScope.launch { dao.delete(key) }

    /**
     * A public-key comment is the free-text tail of the line; keep it a single token by collapsing
     * whitespace to '_'. The label shown in the app keeps its spaces — only the embedded comment
     * is normalized.
     */
    private fun commentFor(label: String): String =
        label.trim().replace(Regex("\\s+"), "_")

    /** Replaces the comment (3rd field) of an OpenSSH public key line with [comment]. */
    private fun withComment(publicKey: String, comment: String): String {
        val parts = publicKey.trim().split(Regex("\\s+"), limit = 3)
        if (parts.size < 2) return publicKey            // malformed — leave as-is
        val base = "${parts[0]} ${parts[1]}"
        return if (comment.isBlank()) base else "$base $comment"
    }
}
