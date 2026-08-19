package com.prestonhill.questgiver.data.local.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.prestonhill.questgiver.data.local.database.dao.HabitDao
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitLogEntity

@Database(
    entities = [
        HabitEntity::class,
        HabitLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class QuestGiverDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
}