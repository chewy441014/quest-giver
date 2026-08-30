package com.prestonhill.questgiver.data.local.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "task_logs",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = [
                "taskId",
                "scheduledEpochDay",
            ],
        ),
        Index(
            value = ["completionTimestampMillis"],
        ),
        Index(
            value = ["reversesLogId"],
            unique = true,
        ),
    ],
)
data class TaskLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Deleted when associated task is deleted
    val taskId: Long,

    // These snapshots keep History useful after archival of associated task.
    val taskNameSnapshot: String,
    val categorySnapshot: String? = null,

    // Identifies the app day this completion satisfied.
    val scheduledEpochDay: Long,

    val dueMinuteOfDaySnapshot: Int? = null,

    // When the task was completed.
    val completionTimestampMillis: Long,

    // When this record or correction was created.
    val recordedTimestampMillis: Long,

    // +1 completes; -1 corrects that completion.
    val delta: Int,

    val reversesLogId: Long? = null,
) {
    init {
        require(delta == 1 || delta == -1)

        require(
            (delta == 1 && reversesLogId == null) ||
                    (delta == -1 &&
                            reversesLogId != null)
        )
    }
}