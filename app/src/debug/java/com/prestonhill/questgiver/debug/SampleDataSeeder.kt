package com.prestonhill.questgiver.debug

import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.repository.AppSettingsRepository
import com.prestonhill.questgiver.data.repository.ComposedNutritionItemDraft
import com.prestonhill.questgiver.data.repository.FoodLogDraft
import com.prestonhill.questgiver.data.repository.NutritionComponentDraft
import com.prestonhill.questgiver.data.repository.NutritionItemDraft
import com.prestonhill.questgiver.data.repository.NutritionRepository
import com.prestonhill.questgiver.data.repository.NutritionValuesInput
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class SampleDataResult(
    val nutritionItems: Int,
    val foodLogs: Int,
    val tasks: Int = 0,
    val taskLogs: Int = 0,
    val habits: Int = 0,
    val habitLogs: Int = 0,
)

class SampleDataSeeder(
    private val database:
    QuestGiverDatabase,
    private val settingsRepository:
    AppSettingsRepository,
    private val clock: Clock,
) {
    private val nutritionRepository =
        NutritionRepository(database)

    suspend fun replaceAll():
            SampleDataResult =
        withContext(Dispatchers.IO) {
            database.clearAllTables()

            settingsRepository
                .setNutritionGoals(
                    calorieGoal = 1_500.0,
                    maximumCalorieGoal =
                        2_300.0,
                    proteinGoalGrams =
                        40.0,
                    maximumProteinGoalGrams =
                        160.0,
                )

            val settings =
                settingsRepository
                    .settings
                    .first()

            val zone =
                zoneFor(
                    daylightSavingEnabled =
                        settings
                            .daylightSavingEnabled
                )

            val calculator =
                AppDayCalculator(
                    dayBoundary =
                        settings.dayBoundary,
                    zoneId = zone,
                )

            val currentDate =
                calculator
                    .containing(
                        clock.millis()
                    )
                    .date

            seedNutrition(
                currentDate = currentDate,
                calculator = calculator,
            )

            val nutrition =
                seedNutrition(
                    currentDate = currentDate,
                    calculator = calculator,
                )

            val tasks =
                TaskSampleDataSeeder(
                    database = database,
                    clock = clock,
                )
                    .seed(
                        currentDate = currentDate,
                        calculator = calculator,
                    )

            val habits =
                HabitSampleDataSeeder(
                    database = database,
                    clock = clock,
                )
                    .seed(
                        currentDate = currentDate,
                        calculator = calculator,
                    )

            nutrition.copy(
                tasks = tasks.tasks,
                taskLogs = tasks.logs,
                habits = habits.habits,
                habitLogs = habits.logs,
            )
        }

    private suspend fun seedNutrition(
        currentDate: LocalDate,
        calculator: AppDayCalculator,
    ): SampleDataResult {
        val createdAt =
            calculator
                .forDate(
                    currentDate.minusDays(450)
                )
                .startTimestampMillis

        var itemCount = 0
        var logCount = 0

        suspend fun item(
            name: String,
            label: String? = null,
            calories: Double,
            protein: Double,
        ): Long {
            val id =
                nutritionRepository
                    .createItem(
                        draft =
                            NutritionItemDraft(
                                name = name,
                                versionLabel =
                                    label,
                                nutrition =
                                    NutritionValuesInput
                                        .Per100Grams(
                                            calories =
                                                calories,
                                            proteinGrams =
                                                protein,
                                        ),
                            ),
                        timestampMillis =
                            createdAt +
                                    itemCount *
                                    1_000L,
                    )

            itemCount += 1
            return id
        }

        suspend fun log(
            itemId: Long,
            date: LocalDate,
            time: LocalTime,
            weight: Double,
        ) {
            requireNotNull(
                nutritionRepository
                    .createLog(
                        draft =
                            FoodLogDraft(
                                itemId = itemId,
                                consumedAtEpochMillis =
                                    calculator
                                        .timestampFor(
                                            appDate =
                                                date,
                                            time = time,
                                        ),
                                weightGrams =
                                    weight,
                            ),
                        timestampMillis =
                            clock.millis(),
                    )
            )

            logCount += 1
        }

        val oats =
            item(
                name = "Rolled oats",
                calories = 389.0,
                protein = 16.9,
            )

        val chicken =
            item(
                name = "Chicken breast",
                calories = 165.0,
                protein = 31.0,
            )

        val rice =
            item(
                name = "Cooked rice",
                calories = 130.0,
                protein = 2.7,
            )

        val broccoli =
            item(
                name = "Broccoli",
                calories = 35.0,
                protein = 2.4,
            )

        val peanutButter =
            item(
                name = "Peanut butter",
                calories = 588.0,
                protein = 25.0,
            )

        val yogurtA =
            item(
                name = "Greek yogurt",
                label = "Brand A",
                calories = 59.0,
                protein = 10.0,
            )

        val yogurtB =
            item(
                name = "Greek yogurt",
                label = "Brand B",
                calories = 73.0,
                protein = 9.0,
            )

        val sauce =
            item(
                name = "Bowl sauce",
                calories = 80.0,
                protein = 1.0,
            )

        val exactMinimum =
            item(
                name =
                    "Exact minimum test meal",
                calories = 1_500.0,
                protein = 40.0,
            )

        val exactMaximum =
            item(
                name =
                    "Exact maximum test meal",
                calories = 2_300.0,
                protein = 160.0,
            )

        val bowl =
            nutritionRepository
                .createComposedItem(
                    draft =
                        ComposedNutritionItemDraft(
                            name =
                                "Chicken rice bowl",
                            versionLabel =
                                "Meal prep",
                            components =
                                listOf(
                                    NutritionComponentDraft(
                                        itemId =
                                            chicken,
                                        gramsPer100g =
                                            40.0,
                                    ),
                                    NutritionComponentDraft(
                                        itemId = rice,
                                        gramsPer100g =
                                            35.0,
                                    ),
                                    NutritionComponentDraft(
                                        itemId =
                                            broccoli,
                                        gramsPer100g =
                                            15.0,
                                    ),
                                    NutritionComponentDraft(
                                        itemId = sauce,
                                        gramsPer100g =
                                            10.0,
                                    ),
                                ),
                        ),
                    timestampMillis =
                        createdAt +
                                itemCount *
                                1_000L,
                )

        itemCount += 1

        for (offset in 0L..419L) {
            val date =
                currentDate.minusDays(offset)

            if (offset > 120L) {
                if (offset % 4L == 0L) {
                    log(
                        itemId = bowl,
                        date = date,
                        time =
                            LocalTime.of(
                                18,
                                30,
                            ),
                        weight =
                            800.0 +
                                    (
                                            offset % 5L
                                            ) * 40.0,
                    )
                }

                continue
            }

            when {
                offset == 3L ->
                    log(
                        itemId =
                            exactMinimum,
                        date = date,
                        time =
                            LocalTime.of(
                                18,
                                0,
                            ),
                        weight = 100.0,
                    )

                offset == 4L ->
                    log(
                        itemId =
                            exactMaximum,
                        date = date,
                        time =
                            LocalTime.of(
                                18,
                                0,
                            ),
                        weight = 100.0,
                    )

                offset % 11L == 10L -> {
                    // Deliberately unlogged.
                }

                offset % 17L == 16L -> {
                    log(
                        itemId = oats,
                        date = date,
                        time =
                            LocalTime.of(
                                8,
                                0,
                            ),
                        weight = 80.0,
                    )

                    log(
                        itemId = yogurtA,
                        date = date,
                        time =
                            LocalTime.of(
                                12,
                                0,
                            ),
                        weight = 150.0,
                    )
                }

                offset % 23L == 22L -> {
                    log(
                        itemId = bowl,
                        date = date,
                        time =
                            LocalTime.of(
                                18,
                                0,
                            ),
                        weight = 1_400.0,
                    )

                    log(
                        itemId =
                            peanutButter,
                        date = date,
                        time =
                            LocalTime.of(
                                14,
                                0,
                            ),
                        weight = 100.0,
                    )

                    log(
                        itemId = oats,
                        date = date,
                        time =
                            LocalTime.of(
                                8,
                                0,
                            ),
                        weight = 150.0,
                    )
                }

                else -> {
                    log(
                        itemId = oats,
                        date = date,
                        time =
                            LocalTime.of(
                                8,
                                0,
                            ),
                        weight =
                            90.0 +
                                    (
                                            offset % 4L
                                            ) * 10.0,
                    )

                    log(
                        itemId = bowl,
                        date = date,
                        time =
                            LocalTime.of(
                                18,
                                30,
                            ),
                        weight =
                            900.0 +
                                    (
                                            offset % 5L
                                            ) * 40.0,
                    )

                    log(
                        itemId =
                            if (
                                offset % 4L == 0L
                            ) {
                                yogurtA
                            } else {
                                yogurtB
                            },
                        date = date,
                        time =
                            LocalTime.of(
                                12,
                                30,
                            ),
                        weight =
                            180.0 +
                                    (
                                            offset % 3L
                                            ) * 20.0,
                    )
                }
            }
        }

        val removal =
            nutritionRepository.removeItem(
                itemId = sauce,
                timestampMillis =
                    clock.millis(),
            )

        check(
            removal ==
                    com.prestonhill.questgiver
                        .data.repository
                        .NutritionItemRemovalResult
                        .ARCHIVED
        )

        return SampleDataResult(
            nutritionItems = itemCount,
            foodLogs = logCount,
        )
    }

    private fun zoneFor(
        daylightSavingEnabled: Boolean,
    ): ZoneId =
        if (daylightSavingEnabled) {
            clock.zone
        } else {
            clock.zone.rules
                .getStandardOffset(
                    clock.instant()
                )
        }
}