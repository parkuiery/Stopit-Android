package com.uiery.keep.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ManualLockTimePolicyTest {

    @Test
    fun instantDeadlineStaysActiveAfterTimezoneChanges() {
        val storedDeadline = ManualLockTimePolicy.encodeDeadline(Instant.parse("2026-06-02T10:00:00Z"))

        assertTrue(
            ManualLockTimePolicy.isActiveAt(
                storedDeadline,
                now = Instant.parse("2026-06-02T09:30:00Z"),
            ),
        )
        assertTrue(
            ManualLockTimePolicy.isActiveAt(
                storedDeadline,
                now = Instant.parse("2026-06-02T09:30:00Z"),
                zone = ZoneId.of("Asia/Seoul"),
            ),
        )
        assertFalse(
            ManualLockTimePolicy.isActiveAt(
                storedDeadline,
                now = Instant.parse("2026-06-02T10:00:00Z"),
                zone = ZoneId.of("America/Los_Angeles"),
            ),
        )
    }

    @Test
    fun legacyLocalDateTimeDeadlineFallsBackToCurrentZone() {
        val legacyDeadline = "2026-06-02T10:00:00"

        assertTrue(
            ManualLockTimePolicy.isActiveAt(
                legacyDeadline,
                now = LocalDateTime.of(2026, 6, 2, 9, 59).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                zone = ZoneId.of("Asia/Seoul"),
            ),
        )
        assertFalse(
            ManualLockTimePolicy.isActiveAt(
                legacyDeadline,
                now = LocalDateTime.of(2026, 6, 2, 10, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                zone = ZoneId.of("Asia/Seoul"),
            ),
        )
    }

    @Test
    fun deadlineCanBeRenderedAsLocalDateTimeForExistingLockRouteUi() {
        val storedDeadline = ManualLockTimePolicy.encodeDeadline(Instant.parse("2026-06-02T10:00:00Z"))

        assertEquals(
            LocalDateTime.of(2026, 6, 2, 19, 0),
            ManualLockTimePolicy.toLocalDateTime(storedDeadline, ZoneId.of("Asia/Seoul")),
        )
    }
}
