package com.prestonhill.questgiver.data.local.database.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: HabitCategoryDb,
    val displayOrder: Int,
    val isVisibleInHistory: Boolean = true,
    val allowsMultipleCompletions: Boolean = false,

    val scheduleType: HabitScheduleTypeDb,
    val scheduleTarget: Int = 1,
    val intervalDays: Int? = null,
    val intervalBasis: HabitIntervalBasisDb? = null,
    val fixedScheduleAnchorEpochDay: Long? = null,
    val extraCompletionsMoveNextDueDate: Boolean = false,
    val scheduleVisibility: HabitScheduleVisibilityDb =
        HabitScheduleVisibilityDb.ALWAYS,

    val createdAtEpochMillis: Long,
    val archivedAtEpochMillis: Long? = null
)

enum class HabitCategoryDb {
    MORNING,
    ANYTIME,
    BEFORE_BED
}

enum class HabitScheduleTypeDb {
    DAILY,
    WEEKLY_TARGET,
    INTERVAL
}

enum class HabitIntervalBasisDb {
    FIXED_SCHEDULE,
    FROM_COMPLETION
}

enum class HabitScheduleVisibilityDb {
    ALWAYS,
    WHEN_DUE,
    HIDE_AFTER_TARGET
}