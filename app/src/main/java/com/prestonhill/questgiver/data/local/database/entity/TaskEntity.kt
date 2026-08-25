package com.prestonhill.questgiver.data.local.database.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,
    val category: String? = null,
    val displayOrder: Int = 0,

    val scheduleType: TaskScheduleTypeDb,

    // Used by dated one-time tasks.
    // Null means the task can be done anytime.
    val scheduledEpochDay: Long? = null,

    // First date on which a recurring task may appear.
    val recurrenceStartEpochDay: Long? = null,

    // Monday is bit 0 and Sunday is bit 6.
    // Used by WEEKLY_DAYS.
    val weekdaysMask: Int? = null,

    val intervalDays: Int? = null,
    val intervalBasis: TaskIntervalBasisDb? = null,

    // Minutes after midnight. Null means no due time.
    val dueMinuteOfDay: Int? = null,

    // Keeps a dated one-time task visible after its date/time.
    val remainsVisibleAfterDue: Boolean = false,

    val createdAtEpochMillis: Long,
    val archivedAtEpochMillis: Long? = null,
)

enum class TaskScheduleTypeDb {
    ONE_TIME,
    DAILY,
    WEEKLY_DAYS,
    INTERVAL,
}

enum class TaskIntervalBasisDb {
    FIXED_SCHEDULE,
    FROM_COMPLETION,
}