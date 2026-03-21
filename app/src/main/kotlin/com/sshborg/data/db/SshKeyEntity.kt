package com.sshborg.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ssh_keys")
data class SshKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    /** Key type: RSA, ECDSA, ED25519 */
    val keyType: String,
    /**
     * Plain-text PEM private key. Empty string when [encryptedBlob] is set.
     * Use [com.sshborg.data.KeystoreManager.getPrivateKeyPem] to read the key.
     */
    val privateKeyPem: String = "",
    /** OpenSSH public key string (e.g. "ssh-ed25519 AAAA...") */
    val publicKey: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** AES-GCM blob (Base64 IV||ciphertext) set when Keystore encryption is enabled. */
    val encryptedBlob: String? = null,
)
