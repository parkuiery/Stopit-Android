package com.uiery.keep.datastore

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Manual/timer lock deadline storage policy.
 *
 * New values are stored as ISO-8601 instants so timezone/DST changes do not alter the remaining
 * real lock duration. Legacy timezone-less [LocalDateTime] strings remain readable as a fallback
 * for already-installed devices.
 */
object ManualLockTimePolicy {
    fun encodeDeadline(deadline: Instant): String = deadline.toString()

    fun isActiveAt(
        storedDeadline: String?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val deadline = parseDeadline(storedDeadline = storedDeadline, zone = zone) ?: return false
        return now.isBefore(deadline)
    }

    fun toLocalDateTime(
        storedDeadline: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDateTime? = parseDeadline(storedDeadline = storedDeadline, zone = zone)
        ?.atZone(zone)
        ?.toLocalDateTime()

    private fun parseDeadline(storedDeadline: String?, zone: ZoneId): Instant? {
        if (storedDeadline.isNullOrBlank()) return null
        return runCatching { Instant.parse(storedDeadline) }
            .getOrElse {
                runCatching {
                    LocalDateTime.parse(storedDeadline).atZone(zone).toInstant()
                }.getOrNull()
            }
    }
}
