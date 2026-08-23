package com.prestonhill.questgiver.feature.habits

import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleVisibilityDb

object HabitVisibilityEvaluator {
    fun shouldShow(
        visibility: HabitScheduleVisibilityDb,
        evaluation: HabitScheduleEvaluation
    ): Boolean =
        when (visibility) {
            HabitScheduleVisibilityDb.ALWAYS ->
                true

            HabitScheduleVisibilityDb.WHEN_DUE ->
                evaluation.dueStatus ==
                        HabitDueStatus.DUE

            HabitScheduleVisibilityDb.HIDE_AFTER_TARGET ->
                evaluation.scheduleCompletionCount <
                        evaluation.scheduleTarget
        }
}