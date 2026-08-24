package com.prestonhill.questgiver.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class AppDay(
    val date: LocalDate,
    val startTimestampMillis: Long,
    val endTimestampMillis: Long
) {
    operator fun contains(timestampMillis: Long): Boolean =
        timestampMillis in
                startTimestampMillis until endTimestampMillis
}

class AppDayCalculator(
    private val dayBoundary: LocalTime,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun containing(timestampMillis: Long): AppDay {
        val localDateTime =
            Instant.ofEpochMilli(timestampMillis)
                .atZone(zoneId)
                .toLocalDateTime()

        val appDate =
            if (localDateTime.toLocalTime().isBefore(dayBoundary)) {
                localDateTime.toLocalDate().minusDays(1)
            } else {
                localDateTime.toLocalDate()
            }

        return forDate(appDate)
    }

    fun forDate(date: LocalDate): AppDay {
        val start =
            date.atTime(dayBoundary)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()

        val end =
            date.plusDays(1)
                .atTime(dayBoundary)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()

        return AppDay(
            date = date,
            startTimestampMillis = start,
            endTimestampMillis = end
        )
    }

    fun timestampFor(
        appDate: LocalDate,
        time: LocalTime,
    ): Long {
        val calendarDate =
            if (time.isBefore(dayBoundary)) {
                appDate.plusDays(1)
            } else {
                appDate
            }

        return calendarDate
            .atTime(time)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}