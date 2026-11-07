package com.showroom.kiosk.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinGuardTest {
    @Test
    fun acceptsThePinThatWasSet() {
        val state = PinGuard.create("123456")
        val (result, next) = PinGuard.check(state, "123456", nowEpochMs = 1_000L)
        assertTrue(result is PinResult.Accepted)
        assertEquals(0, next.failures)
    }

    @Test
    fun locksAfterFiveFailuresAndDoesNotCountDuringTheWait() {
        var state = PinGuard.create("123456")
        val start = 10_000L
        repeat(4) { index ->
            val (result, next) = PinGuard.check(state, "000000", start + index)
            assertTrue(result is PinResult.Rejected)
            assertEquals(0L, (result as PinResult.Rejected).lockoutUntilEpochMs)
            state = next
        }
        val (locked, afterFifth) = PinGuard.check(state, "000000", start + 4)
        assertTrue(locked is PinResult.Rejected)
        assertEquals(start + 4 + 60_000L, (locked as PinResult.Rejected).lockoutUntilEpochMs)
        val (waiting, unchanged) = PinGuard.check(afterFifth, "000000", start + 5)
        assertTrue(waiting is PinResult.Waiting)
        assertEquals(afterFifth, unchanged)
    }

    @Test
    fun longerLockouts() {
        assertEquals(600_000L, PinGuard.lockoutMs(10))
        assertEquals(3_600_000L, PinGuard.lockoutMs(15))
    }

    @Test(expected = ConfigException::class)
    fun rejectsAShortPin() {
        PinGuard.create("12345")
    }
}
