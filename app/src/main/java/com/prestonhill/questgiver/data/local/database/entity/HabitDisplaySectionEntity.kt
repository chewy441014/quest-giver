package com.prestonhill.questgiver.data.local.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "habit_display_sections",
    indices = [
        Index(
            value = ["name"],
            unique = true,
        ),
    ],
)
data class HabitDisplaySectionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val displayOrder: Int,
)

object DefaultHabitDisplaySections {
    const val MORNING_ID = "MORNING"
    const val ANYTIME_ID = "ANYTIME"
    const val BEFORE_BED_ID = "BEFORE_BED"

    val all =
        listOf(
            HabitDisplaySectionEntity(
                id = MORNING_ID,
                name = "Morning",
                displayOrder = 0,
            ),
            HabitDisplaySectionEntity(
                id = ANYTIME_ID,
                name = "Anytime",
                displayOrder = 1,
            ),
            HabitDisplaySectionEntity(
                id = BEFORE_BED_ID,
                name = "Before bed",
                displayOrder = 2,
            ),
        )
}