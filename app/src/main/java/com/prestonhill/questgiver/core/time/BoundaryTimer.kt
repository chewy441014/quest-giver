package com.prestonhill.questgiver.core.time

import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

interface BoundaryTimer {
    suspend fun pause(milliseconds: Long)
}

object RealBoundaryTimer : BoundaryTimer {
    override suspend fun pause(milliseconds: Long) {
        delay(milliseconds.milliseconds)
    }
}