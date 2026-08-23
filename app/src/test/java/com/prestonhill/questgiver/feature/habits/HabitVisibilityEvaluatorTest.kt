package com.prestonhill.questgiver.feature.habits

import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleVisibilityDb
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitVisibilityEvaluatorTest {
    @Test
    fun alwaysRemainsVisible() {
        assertTrue(
            HabitVisibilityEvaluator.shouldShow(
                HabitScheduleVisibilityDb.ALWAYS,
                evaluation(
                    count = 1,
                    status = HabitDueStatus.COMPLETED
                )
            )
        )
    }

    @Test
    fun dueOnlyRequiresDueStatus() {
        assertTrue(
            HabitVisibilityEvaluator.shouldShow(
                HabitScheduleVisibilityDb.WHEN_DUE,
                evaluation(
                    count = 0,
                    status = HabitDueStatus.DUE
                )
            )
        )

        assertFalse(
            HabitVisibilityEvaluator.shouldShow(
                HabitScheduleVisibilityDb.WHEN_DUE,
                evaluation(
                    count = 0,
                    status = HabitDueStatus.NOT_DUE
                )
            )
        )
    }

    @Test
    fun targetHidesWhenReached() {
        assertTrue(
            HabitVisibilityEvaluator.shouldShow(
                HabitScheduleVisibilityDb.HIDE_AFTER_TARGET,
                evaluation(
                    count = 2,
                    target = 3
                )
            )
        )

        assertFalse(
            HabitVisibilityEvaluator.shouldShow(
                HabitScheduleVisibilityDb.HIDE_AFTER_TARGET,
                evaluation(
                    count = 3,
                    target = 3
                )
            )
        )
    }

    private fun evaluation(
        count: Int,
        target: Int = 1,
        status: HabitDueStatus = HabitDueStatus.DUE
    ) =
        HabitScheduleEvaluation(
            dailyCompletionCount = count,
            scheduleCompletionCount = count,
            scheduleTarget = target,
            streakCount = 0,
            dueStatus = status
        )
}