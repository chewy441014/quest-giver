package com.prestonhill.questgiver.data.local.database

import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override suspend fun migrate(
            connection: SQLiteConnection,
        ) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS
                    `habit_display_sections` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `displayOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent()
            )

            connection.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS
                    `index_habit_display_sections_name`
                ON `habit_display_sections` (`name`)
                """.trimIndent()
            )

            insertDefaultHabitDisplaySections(
                connection
            )

            connection.execSQL(
                """
                ALTER TABLE `habits`
                ADD COLUMN `historyCategory` TEXT
                """.trimIndent()
            )
        }
    }

val HABIT_DISPLAY_SECTION_CALLBACK =
    object : RoomDatabase.Callback() {
        override suspend fun onCreate(
            connection: SQLiteConnection,
        ) {
            insertDefaultHabitDisplaySections(
                connection
            )
        }
    }

private suspend fun insertDefaultHabitDisplaySections(
    connection: SQLiteConnection,
) {
    connection.execSQL(
        """
        INSERT OR IGNORE INTO
            `habit_display_sections`
            (`id`, `name`, `displayOrder`)
        VALUES
            ('MORNING', 'Morning', 0),
            ('ANYTIME', 'Anytime', 1),
            ('BEFORE_BED', 'Before bed', 2)
        """.trimIndent()
    )
}