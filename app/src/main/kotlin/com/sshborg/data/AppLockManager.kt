package com.sshborg.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The in-app lock secret (a numeric PIN or an alphanumeric passphrase) used by the
 * [AppPreferences.LOCK_SECRET] lock mode. Works on any device — including TVs, where
 * biometric/device-credential locks are usually unavailable — because it depends on
 * nothing but a secret the user chose.
 *
 * The secret is never stored: [setSecret] keeps only a salted PBKDF2 hash, and [verify]
 * re-derives and compares in constant time. A low-entropy PIN can't be made
 * brute-force-proof if the hash leaks, so verification is paired with an escalating
 * lockout after repeated failures. This is a UI authorization gate (same class as the
 * biometric lock), not encryption of the stored data.
 */
class AppLockManager(private val prefs: AppPreferences) {

    enum class Kind(val stored: String) {
        PIN("pin"),
        PASSPHRASE("passphrase");

        companion object {
            fun from(stored: String?): Kind? = entries.firstOrNull { it.stored == stored }
        }
    }

    sealed interface VerifyResult {
        object Success : VerifyResult
        /** Wrong secret; [attemptsRemaining] tries left before the next lockout. */
        data class Wrong(val attemptsRemaining: Int) : VerifyResult
        /** Too many failures; retry allowed after [secondsRemaining]. */
        data class LockedOut(val secondsRemaining: Long) : VerifyResult
    }

    suspend fun isConfigured(): Boolean = prefs.readLockSecret() != null

    suspend fun kind(): Kind? = Kind.from(prefs.readLockSecret()?.kind)

    /** Stores [secret] as a fresh salted hash and clears any throttling state. */
    suspend fun setSecret(kind: Kind, secret: CharArray) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(secret, salt, ITERATIONS)
        prefs.writeLockSecret(
            AppPreferences.LockSecret(kind.stored, b64(salt), b64(hash), ITERATIONS)
        )
        prefs.writeLockAttempts(0, 0L)
    }

    suspend fun clear() = prefs.clearLockSecret()

    /** Seconds still to wait before another attempt is allowed, or 0 if unlocked. */
    suspend fun lockoutRemainingSeconds(): Long {
        val (_, until) = prefs.readLockAttempts()
        val remainingMs = until - System.currentTimeMillis()
        return if (remainingMs > 0) (remainingMs + 999) / 1000 else 0
    }

    /** Verifies [input] against the stored secret, applying lockout throttling. */
    suspend fun verify(input: CharArray): VerifyResult {
        val record = prefs.readLockSecret() ?: return VerifyResult.Success
        val (failed, until) = prefs.readLockAttempts()
        val now = System.currentTimeMillis()
        if (now < until) return VerifyResult.LockedOut((until - now + 999) / 1000)

        val actual = pbkdf2(input, unb64(record.saltB64), record.iterations)
        val matches = MessageDigest.isEqual(actual, unb64(record.hashB64))
        if (matches) {
            prefs.writeLockAttempts(0, 0L)
            return VerifyResult.Success
        }

        val newFailed = failed + 1
        return if (newFailed % ATTEMPTS_PER_LOCKOUT == 0) {
            val lockoutNumber = newFailed / ATTEMPTS_PER_LOCKOUT      // 1, 2, 3, …
            val secs = (BASE_LOCKOUT_SECONDS shl (lockoutNumber - 1)).coerceAtMost(MAX_LOCKOUT_SECONDS)
            prefs.writeLockAttempts(newFailed, now + secs * 1000)
            VerifyResult.LockedOut(secs)
        } else {
            prefs.writeLockAttempts(newFailed, 0L)
            VerifyResult.Wrong(ATTEMPTS_PER_LOCKOUT - (newFailed % ATTEMPTS_PER_LOCKOUT))
        }
    }

    /**
     * Plain match against the stored secret, with no throttling side effects. Used to confirm the
     * current secret before changing it — the app is already unlocked there, so it must not touch
     * the failed-attempt counters that guard the unlock screen.
     */
    suspend fun checkSecret(input: CharArray): Boolean {
        val record = prefs.readLockSecret() ?: return false
        val actual = pbkdf2(input, unb64(record.saltB64), record.iterations)
        return MessageDigest.isEqual(actual, unb64(record.hashB64))
    }

    private fun pbkdf2(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        private const val SALT_BYTES = 16
        private const val ITERATIONS = 210_000
        private const val KEY_BITS = 256

        /** Failures allowed before a lockout kicks in. */
        const val ATTEMPTS_PER_LOCKOUT = 5
        private const val BASE_LOCKOUT_SECONDS = 30L
        private const val MAX_LOCKOUT_SECONDS = 15L * 60L
    }
}
