package com.uiery.keep.domain.parentmode

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * A guardian PIN in the only form it is allowed to be stored: a salted hash.
 *
 * The PIN has to outlive the setup screen — the parent types it once at the start and again, hours
 * later, to end the session — but the plaintext must not. Keeping the digest makes the later check
 * possible without the stored session ever holding something that could be read back and retyped.
 */
internal data class ParentModeGuardianPinDigest(
    val hash: String,
    val salt: String,
)

/**
 * The result of holding a typed PIN against the one its session was started with.
 *
 * [opensGate] is deliberately separate from which of the three [ParentModeGuardianPinVerdict]
 * values it is: a missing digest opens the gate but is not a successful match, and analytics has to
 * be able to tell those apart.
 */
internal enum class ParentModeGuardianPinVerdict(val opensGate: Boolean) {
    Matched(opensGate = true),
    Mismatched(opensGate = false),

    /**
     * The session carries no stored PIN, which happens only for a session that was already running
     * when the digest started being saved. Refusing it would leave a parent unable to end a lock
     * they set themselves, so the gate stands aside; the session still expires on its own.
     */
    NoStoredPin(opensGate = true),
}

internal object ParentModeGuardianPin {
    /**
     * PBKDF2 rather than a bare hash. A four-digit PIN has ten thousand candidates, so anything
     * cheap to compute is exhausted instantly by whoever gets hold of the preferences file. The
     * stretch does not make that impossible — nothing can, at four digits — it only makes it cost
     * more than the phone it would unlock.
     */
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun digest(
        pin: String,
        random: SecureRandom = SecureRandom(),
    ): ParentModeGuardianPinDigest {
        val salt = ByteArray(SALT_LENGTH_BYTES).also(random::nextBytes)
        return ParentModeGuardianPinDigest(
            hash = encode(hash(pin = pin, salt = salt)),
            salt = encode(salt),
        )
    }

    fun verify(
        pin: String,
        digest: ParentModeGuardianPinDigest?,
    ): ParentModeGuardianPinVerdict = when {
        digest == null -> ParentModeGuardianPinVerdict.NoStoredPin
        matches(pin = pin, digest = digest) -> ParentModeGuardianPinVerdict.Matched
        else -> ParentModeGuardianPinVerdict.Mismatched
    }

    /** Constant-time on purpose: the comparison itself must not leak how much of the PIN matched. */
    fun matches(
        pin: String,
        digest: ParentModeGuardianPinDigest,
    ): Boolean {
        val salt = decodeOrNull(digest.salt) ?: return false
        val expected = decodeOrNull(digest.hash) ?: return false
        return MessageDigest.isEqual(hash(pin = pin, salt = salt), expected)
    }

    private fun hash(
        pin: String,
        salt: ByteArray,
    ): ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            javax.crypto.SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decodeOrNull(value: String): ByteArray? =
        runCatching { Base64.getDecoder().decode(value) }.getOrNull()
}
