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
import com.prestonhill.questgiver.data.local.database.dao.NutritionDao
import com.prestonhill.questgiver.data.local.database.entity.FoodLogEntity
import com.prestonhill.questgiver.data.local.database.entity.NutritionComponentEntity
import com.prestonhill.questgiver.data.local.database.entity.NutritionItemEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitDisplaySectionEntity

@Database(
    entities = [
        HabitEntity::class,
        HabitLogEntity::class,
        TaskEntity::class,
        TaskLogEntity::class,
        NutritionItemEntity::class,
        NutritionComponentEntity::class,
        FoodLogEntity::class,
        HabitDisplaySectionEntity::class,
    ],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(
            from = 4,
            to = 5,
        ),
    ],
)
abstract class QuestGiverDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun taskDao(): TaskDao
    abstract fun nutritionDao(): NutritionDao
}