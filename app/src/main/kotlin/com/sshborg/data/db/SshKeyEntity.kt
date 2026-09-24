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
    /**
     * Passphrase of an encrypted private key, in plain text. Null when the key needs none, and
     * null when [encryptedPassphrase] holds it instead.
     *
     * The key material is kept exactly as it was imported, still encrypted, and the passphrase
     * is stored beside it so a connection can decrypt it without asking again: re-serialising a
     * decrypted key is not possible for every type (JSch cannot write ed25519), and keeping the
     * original bytes means the key on this device is no less protected than the file it came
     * from. Read it with [com.sshborg.data.KeystoreManager.getPassphrase].
     */
    val passphrase: String? = null,
    /** AES-GCM blob of [passphrase], set when Keystore encryption is enabled. */
    val encryptedPassphrase: String? = null,
)
