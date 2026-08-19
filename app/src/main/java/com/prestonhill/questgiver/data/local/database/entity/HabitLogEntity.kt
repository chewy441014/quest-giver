package com.prestonhill.questgiver.data.local.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "habit_logs",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(
            value = ["habitId", "completionTimestampMillis"]
        ),
        Index(
            value = ["reversesLogId"],
            unique = true
        )
    ]
)
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val habitId: Long,

    // The completion being added or reversed.
    val completionTimestampMillis: Long,

    // When this log record was actually created.
    val recordedTimestampMillis: Long,

    // Must be either +1 or -1.
    val delta: Int,

    // Required for a negative record; null for a positive record.
    val reversesLogId: Long? = null
) {
    init {
        require(delta == 1 || delta == -1)

        require(
            (delta == 1 && reversesLogId == null) ||
                    (delta == -1 && reversesLogId != null)
        )
    }
}