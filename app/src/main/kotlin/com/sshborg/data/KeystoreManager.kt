package com.sshborg.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.sshborg.data.db.SshKeyEntity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

/**
 * Encrypts and decrypts private key material using an AES-256-GCM key stored in
 * the Android Keystore. The key never leaves secure hardware.
 *
 * Blob format: Base64(IV_12_bytes || ciphertext)
 */
object KeystoreManager {

    private const val KEY_ALIAS     = "sshborg_key_encryption_v1"
    private const val KEYSTORE      = "AndroidKeyStore"
    private const val ALGORITHM     = "AES/GCM/NoPadding"
    private const val GCM_IV_LEN    = 12
    private const val GCM_TAG_BITS  = 128

    // ── Key management ────────────────────────────────────────────────────────

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).also { it.load(null) }
        ks.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .also { it.init(spec) }
            .generateKey()
    }

    fun hasKey(): Boolean {
        val ks = KeyStore.getInstance(KEYSTORE).also { it.load(null) }
        return ks.containsAlias(KEY_ALIAS)
    }

    fun deleteKey() {
        val ks = KeyStore.getInstance(KEYSTORE).also { it.load(null) }
        if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
    }

    // ── Encrypt / Decrypt ─────────────────────────────────────────────────────

    /** Encrypts [plaintext] and returns Base64(IV || ciphertext). */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv         = cipher.iv                        // 12 bytes, auto-generated
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val blob       = ByteArray(GCM_IV_LEN + ciphertext.size)
        System.arraycopy(iv, 0, blob, 0, GCM_IV_LEN)
        System.arraycopy(ciphertext, 0, blob, GCM_IV_LEN, ciphertext.size)
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    /** Decrypts a blob produced by [encrypt]. */
    fun decrypt(blob: String): String {
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        val iv         = raw.copyOfRange(0, GCM_IV_LEN)
        val ciphertext = raw.copyOfRange(GCM_IV_LEN, raw.size)
        val key        = KeyStore.getInstance(KEYSTORE).also { it.load(null) }
            .getKey(KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /**
     * Returns the plain-text PEM for [entity], decrypting from [encryptedBlob] if present,
     * or falling back to [SshKeyEntity.privateKeyPem] for unencrypted keys.
     * Returns null if neither is available.
     */
    fun getPrivateKeyPem(entity: SshKeyEntity): String? {
        val blob = entity.encryptedBlob
        return when {
            blob != null -> runCatching { decrypt(blob) }.getOrNull()
            entity.privateKeyPem.isNotBlank() -> entity.privateKeyPem
            else -> null
        }
    }
}
