package com.prestonhill.questgiver.data.local.database

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.prestonhill.questgiver.data.local.database.dao.HabitDao
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitLogEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskLogEntity
import com.prestonhill.questgiver.data.local.database.dao.TaskDao

@Database(
    entities = [
        HabitEntity::class,
        HabitLogEntity::class,
        TaskEntity::class,
        TaskLogEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(
            from = 1,
            to = 2,
        ),
    ],
)
abstract class QuestGiverDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun taskDao(): TaskDao
}