package com.showroom.kiosk.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PinState(
    val saltB64: String,
    val hashB64: String,
    val failures: Int,
    val lockoutUntilEpochMs: Long,
)

sealed class PinResult {
    data object Accepted : PinResult()
    data class Rejected(val failures: Int, val lockoutUntilEpochMs: Long) : PinResult()
    data class Waiting(val lockoutUntilEpochMs: Long) : PinResult()
}

object PinHasher {
    private const val ITERATIONS = 120_000
    private const val BITS = 256

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hash(pin: String, saltB64: String): String {
        val salt = Base64.getDecoder().decode(saltB64)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, BITS)
        val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
        return Base64.getEncoder().encodeToString(encoded)
    }

    fun verify(pin: String, saltB64: String, expectedHashB64: String): Boolean {
        return try {
            val actual = Base64.getDecoder().decode(hash(pin, saltB64))
            val expected = Base64.getDecoder().decode(expectedHashB64)
            MessageDigest.isEqual(actual, expected)
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

object PinGuard {
    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 12

    fun isFormatValid(pin: String): Boolean {
        return pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }
    }

    fun create(pin: String): PinState {
        if (!isFormatValid(pin)) {
            throw ConfigException("PIN must be $MIN_LENGTH to $MAX_LENGTH digits")
        }
        val salt = PinHasher.newSalt()
        return PinState(
            saltB64 = salt,
            hashB64 = PinHasher.hash(pin, salt),
            failures = 0,
            lockoutUntilEpochMs = 0L,
        )
    }

    fun check(state: PinState, pin: String, nowEpochMs: Long): Pair<PinResult, PinState> {
        if (nowEpochMs < state.lockoutUntilEpochMs) {
            return PinResult.Waiting(state.lockoutUntilEpochMs) to state
        }
        if (PinHasher.verify(pin, state.saltB64, state.hashB64)) {
            val cleared = state.copy(failures = 0, lockoutUntilEpochMs = 0L)
            return PinResult.Accepted to cleared
        }
        val failures = state.failures + 1
        val delay = lockoutMs(failures)
        val until = if (delay == 0L) 0L else nowEpochMs + delay
        val next = state.copy(failures = failures, lockoutUntilEpochMs = until)
        return PinResult.Rejected(failures, until) to next
    }

    fun lockoutMs(failures: Int): Long = when {
        failures >= 15 -> 3_600_000L
        failures >= 10 -> 600_000L
        failures >= 5 -> 60_000L
        else -> 0L
    }
}
