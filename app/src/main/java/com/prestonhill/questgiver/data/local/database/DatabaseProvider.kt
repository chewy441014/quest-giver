package com.prestonhill.questgiver.data.local.database

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers

object DatabaseProvider {
    private const val DATABASE_NAME = "quest_giver.db"

    @Volatile
    private var instance: QuestGiverDatabase? = null

    fun get(context: Context): QuestGiverDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder<QuestGiverDatabase>(
                context = context.applicationContext,
                name = DATABASE_NAME
            )
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addMigrations(MIGRATION_5_6)
                .addCallback(
                    HABIT_DISPLAY_SECTION_CALLBACK
                )
                .build()
                .also { database ->
                    instance = database
                }
        }
}