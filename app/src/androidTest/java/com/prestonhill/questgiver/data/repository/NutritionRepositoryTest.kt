package com.prestonhill.questgiver.data.repository

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NutritionRepositoryTest {
    private lateinit var database:
            QuestGiverDatabase

    private lateinit var repository:
            NutritionRepository

    @Before
    fun setup() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        database =
            Room.inMemoryDatabaseBuilder<
                    QuestGiverDatabase
                    >(context)
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(
                    Dispatchers.IO
                )
                .build()

        repository =
            NutritionRepository(database)
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun createCleansAndNormalizesItem(): Unit =
        runBlocking {
            val itemId =
                repository.createItem(
                    draft =
                        NutritionItemDraft(
                            name =
                                "  Greek   Yogurt  ",
                            versionLabel =
                                "  High   protein  ",
                            nutrition =
                                NutritionValuesInput
                                    .Per100Grams(
                                        calories =
                                            92.0,
                                        proteinGrams =
                                            10.0,
                                    ),
                        ),
                    timestampMillis =
                        FIRST_TIME,
                )

            val item =
                requireNotNull(
                    repository.getItem(itemId)
                )

            assertNotNull(item)

            assertEquals(
                "Greek Yogurt",
                item.name,
            )

            assertEquals(
                "greek yogurt",
                item.nameKey,
            )

            assertEquals(0, item.version)

            assertEquals(
                "High protein",
                item.versionLabel,
            )

            assertEquals(
                92.0,
                item.caloriesPer100g,
                TOLERANCE,
            )

            assertEquals(
                10.0,
                item.proteinPer100g,
                TOLERANCE,
            )

            assertEquals(
                FIRST_TIME,
                item.createdAtEpochMillis,
            )

            assertEquals(
                FIRST_TIME,
                item.updatedAtEpochMillis,
            )
        }

    @Test
    fun servingIsConvertedToPer100Grams(): Unit =
        runBlocking {
            val itemId =
                repository.createItem(
                    draft =
                        NutritionItemDraft(
                            name = "Protein bar",
                            nutrition =
                                NutritionValuesInput
                                    .Serving(
                                        weightGrams =
                                            50.0,
                                        calories =
                                            200.0,
                                        proteinGrams =
                                            15.0,
                                    ),
                        ),
                )

            val item =
                requireNotNull(
                    repository.getItem(itemId)
                )

            assertEquals(
                400.0,
                item.caloriesPer100g,
                TOLERANCE,
            )

            assertEquals(
                30.0,
                item.proteinPer100g,
                TOLERANCE,
            )
        }

    @Test
    fun matchingNamesAllocateVersions(): Unit =
        runBlocking {
            val firstId =
                repository.createItem(
                    itemDraft(
                        name = "Greek Yogurt",
                        calories = 80.0,
                    )
                )

            val secondId =
                repository.createItem(
                    itemDraft(
                        name =
                            "  GREEK   yogurt ",
                        calories = 90.0,
                    )
                )

            val first =
                repository.getItem(firstId)

            val second =
                repository.getItem(secondId)

            assertEquals(0, first?.version)
            assertEquals(1, second?.version)

            assertEquals(
                listOf(0, 1),
                repository
                    .getVersions(
                        " greek yogurt "
                    )
                    .map { it.version },
            )
        }

    @Test
    fun updatePreservesIdentityAndVersion(): Unit =
        runBlocking {
            val itemId =
                repository.createItem(
                    draft =
                        itemDraft(
                            name = "Original",
                            calories = 100.0,
                        ),
                    timestampMillis =
                        FIRST_TIME,
                )

            assertTrue(
                repository.updateItem(
                    itemId = itemId,
                    draft =
                        NutritionItemDraft(
                            name = "Updated",
                            versionLabel =
                                "New label",
                            nutrition =
                                NutritionValuesInput
                                    .Per100Grams(
                                        calories =
                                            150.0,
                                        proteinGrams =
                                            20.0,
                                    ),
                        ),
                    timestampMillis =
                        SECOND_TIME,
                )
            )

            val updated =
                requireNotNull(
                    repository.getItem(itemId)
                )

            assertEquals(itemId, updated.id)
            assertEquals(0, updated.version)

            assertEquals(
                FIRST_TIME,
                updated.createdAtEpochMillis,
            )

            assertEquals(
                SECOND_TIME,
                updated.updatedAtEpochMillis,
            )

            assertEquals(
                "Updated",
                updated.name,
            )

            assertEquals(
                150.0,
                updated.caloriesPer100g,
                TOLERANCE,
            )

            assertEquals(
                20.0,
                updated.proteinPer100g,
                TOLERANCE,
            )
        }

    @Test
    fun saveAsCreatesNextVersion(): Unit =
        runBlocking {
            val originalId =
                repository.createItem(
                    draft =
                        itemDraft(
                            name = "Oats",
                            calories = 350.0,
                        ),
                    timestampMillis =
                        FIRST_TIME,
                )

            val newId =
                requireNotNull(
                    repository.saveAsVersion(
                        itemId = originalId,
                        draft = itemDraft(
                            name = "Oats",
                            calories = 380.0,
                        ),
                        timestampMillis = SECOND_TIME,
                    )
                )

            val versions =
                repository.getVersions("Oats")

            assertEquals(
                listOf(0, 1),
                versions.map { it.version },
            )

            assertEquals(
                350.0,
                versions.single {
                    it.version == 0
                }.caloriesPer100g,
                TOLERANCE,
            )

            assertEquals(
                380.0,
                versions.single {
                    it.version == 1
                }.caloriesPer100g,
                TOLERANCE,
            )

            assertEquals(
                SECOND_TIME,
                versions.single {
                    it.version == 1
                }.createdAtEpochMillis,
            )
        }

    @Test
    fun missingItemCannotUpdateOrSaveAs(): Unit =
        runBlocking {
            assertFalse(
                repository.updateItem(
                    itemId = Long.MAX_VALUE,
                    draft = itemDraft(),
                )
            )

            assertNull(
                repository.saveAsVersion(
                    itemId = Long.MAX_VALUE,
                    draft = itemDraft(),
                )
            )

            assertTrue(
                repository.observeAllItems()
                    .first()
                    .isEmpty()
            )
        }

    @Test
    fun invalidInputIsRejected(): Unit =
        runBlocking {
            val invalidDrafts =
                listOf(
                    itemDraft(name = "   "),
                    NutritionItemDraft(
                        name = "Zero weight",
                        nutrition =
                            NutritionValuesInput
                                .Serving(
                                    weightGrams = 0.0,
                                    calories = 100.0,
                                    proteinGrams = 10.0,
                                ),
                    ),
                    itemDraft(calories = -1.0),
                    NutritionItemDraft(
                        name = "Negative protein",
                        nutrition =
                            NutritionValuesInput
                                .Per100Grams(
                                    calories = 100.0,
                                    proteinGrams = -1.0,
                                ),
                    ),
                    itemDraft(
                        calories = Double.NaN
                    ),
                    NutritionItemDraft(
                        name = "Infinite protein",
                        nutrition =
                            NutritionValuesInput
                                .Per100Grams(
                                    calories = 100.0,
                                    proteinGrams =
                                        Double
                                            .POSITIVE_INFINITY,
                                ),
                    ),
                )

            invalidDrafts.forEach { draft ->
                val failure =
                    runCatching {
                        repository.createItem(
                            draft
                        )
                    }

                assertNotNull(
                    failure.exceptionOrNull()
                )
            }

            assertTrue(
                repository.observeAllItems()
                    .first()
                    .isEmpty()
            )
        }

    private fun itemDraft(
        name: String = "Test item",
        calories: Double = 100.0,
        protein: Double = 10.0,
    ): NutritionItemDraft =
        NutritionItemDraft(
            name = name,
            nutrition =
                NutritionValuesInput
                    .Per100Grams(
                        calories = calories,
                        proteinGrams = protein,
                    ),
        )

    private companion object {
        const val FIRST_TIME = 1_000L
        const val SECOND_TIME = 2_000L
        const val TOLERANCE = 0.000_001
    }
}