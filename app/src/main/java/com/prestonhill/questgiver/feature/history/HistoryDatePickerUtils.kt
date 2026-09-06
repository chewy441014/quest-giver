package com.prestonhill.questgiver.feature.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal fun LocalDate.utcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

internal fun Long.utcDate(): LocalDate =
    Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()