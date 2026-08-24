package com.prestonhill.questgiver.core.time

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDayCalculatorTest {
    private val zone = ZoneId.of("America/Chicago")

    @Test
    fun beforeBoundaryUsesPreviousDay() {
        val calculator = AppDayCalculator(
            dayBoundary = LocalTime.of(4, 0),
            zoneId = zone
        )

        val timestamp = timestampFor(2026, 8, 18, 3, 59)

        assertEquals(
            LocalDate.of(2026, 8, 17),
            calculator.containing(timestamp).date
        )
    }

    @Test
    fun atBoundaryUsesNewDay() {
        val calculator = AppDayCalculator(
            dayBoundary = LocalTime.of(4, 0),
            zoneId = zone
        )

        val timestamp = timestampFor(2026, 8, 18, 4, 0)

        assertEquals(
            LocalDate.of(2026, 8, 18),
            calculator.containing(timestamp).date
        )
    }

    @Test
    fun endBoundaryIsExcluded() {
        val calculator = AppDayCalculator(
            dayBoundary = LocalTime.MIDNIGHT,
            zoneId = zone
        )

        val day = calculator.forDate(
            LocalDate.of(2026, 8, 18)
        )

        assertTrue(day.startTimestampMillis in day)
        assertFalse(day.endTimestampMillis in day)
    }

    @Test
    fun springDayIs23Hours() {
        val calculator = AppDayCalculator(
            dayBoundary = LocalTime.MIDNIGHT,
            zoneId = zone
        )

        val day = calculator.forDate(
            LocalDate.of(2026, 3, 8)
        )

        assertEquals(
            23L * 60 * 60 * 1000,
            day.endTimestampMillis -
                    day.startTimestampMillis
        )
    }

    @Test
    fun fallDayIs25Hours() {
        val calculator = AppDayCalculator(
            dayBoundary = LocalTime.MIDNIGHT,
            zoneId = zone
        )

        val day = calculator.forDate(
            LocalDate.of(2026, 11, 1)
        )

        assertEquals(
            25L * 60 * 60 * 1000,
            day.endTimestampMillis -
                    day.startTimestampMillis
        )
    }

    @Test
    fun fixedOffsetDaysAre24Hours() {
        val standardOffset =
            zone.rules.getStandardOffset(
                Instant.parse(
                    "2026-08-23T17:00:00Z"
                )
            )

        val calculator =
            AppDayCalculator(
                dayBoundary = LocalTime.MIDNIGHT,
                zoneId = standardOffset,
            )

        val springDay =
            calculator.forDate(
                LocalDate.of(2026, 3, 8)
            )

        val fallDay =
            calculator.forDate(
                LocalDate.of(2026, 11, 1)
            )

        val expected =
            24L * 60 * 60 * 1000

        assertEquals(
            expected,
            springDay.endTimestampMillis -
                    springDay.startTimestampMillis
        )

        assertEquals(
            expected,
            fallDay.endTimestampMillis -
                    fallDay.startTimestampMillis
        )
    }

    private fun timestampFor(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long =
        LocalDateTime.of(
            year,
            month,
            day,
            hour,
            minute
        )
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
}