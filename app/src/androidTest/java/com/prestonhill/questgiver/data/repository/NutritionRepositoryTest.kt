package com.prestonhill.questgiver.data.repository

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.local.database.dao.NutritionDao
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

    private lateinit var dao:
            NutritionDao

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

        dao = database.nutritionDao()

        repository =
            NutritionRepository(database)
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun composedItemCalculatesNutritionAndOrder(): Unit =
        runBlocking {
            val chickenId =
                repository.createItem(
                    itemDraft(
                        name = "Chicken",
                        calories = 200.0,
                        protein = 20.0,
                    )
                )

            val riceId =
                repository.createItem(
                    itemDraft(
                        name = "Rice",
                        calories = 100.0,
                        protein = 5.0,
                    )
                )

            val parentId =
                repository.createComposedItem(
                    ComposedNutritionItemDraft(
                        name = "Chicken bowl",
                        components = listOf(
                            NutritionComponentDraft(
                                itemId = chickenId,
                                gramsPer100g = 25.0,
                            ),
                            NutritionComponentDraft(
                                itemId = riceId,
                                gramsPer100g = 75.0,
                            ),
                        ),
                    )
                )

            val parent =
                requireNotNull(
                    repository.getItem(parentId)
                )

            assertEquals(
                125.0,
                parent.caloriesPer100g,
                TOLERANCE,
            )

            assertEquals(
                8.75,
                parent.proteinPer100g,
                TOLERANCE,
            )

            val components =
                dao.getComponents(parentId)

            assertEquals(
                listOf(chickenId, riceId),
                components.map {
                    it.componentItemId
                },
            )

            assertEquals(
                listOf(0, 1),
                components.map {
                    it.displayOrder
                },
            )
        }

    @Test
    fun composedItemUsesNextVersion(): Unit =
        runBlocking {
            repository.createItem(
                itemDraft(
                    name = "Breakfast",
                    calories = 100.0,
                )
            )

            val componentId =
                repository.createItem(
                    itemDraft(
                        name = "Component"
                    )
                )

            repository.createComposedItem(
                ComposedNutritionItemDraft(
                    name = "  BREAKFAST ",
                    components = listOf(
                        NutritionComponentDraft(
                            itemId = componentId,
                            gramsPer100g = 100.0,
                        )
                    ),
                )
            )

            assertEquals(
                listOf(0, 1),
                repository
                    .getVersions("breakfast")
                    .map { it.version },
            )
        }

    @Test
    fun composedItemCanContainComposedItem(): Unit =
        runBlocking {
            val baseId =
                repository.createItem(
                    itemDraft(
                        name = "Base",
                        calories = 240.0,
                        protein = 16.0,
                    )
                )

            val firstParentId =
                repository.createComposedItem(
                    ComposedNutritionItemDraft(
                        name = "First parent",
                        components = listOf(
                            NutritionComponentDraft(
                                itemId = baseId,
                                gramsPer100g =
                                    100.0,
                            )
                        ),
                    )
                )

            val secondParentId =
                repository.createComposedItem(
                    ComposedNutritionItemDraft(
                        name = "Second parent",
                        components = listOf(
                            NutritionComponentDraft(
                                itemId =
                                    firstParentId,
                                gramsPer100g =
                                    100.0,
                            )
                        ),
                    )
                )

            val secondParent =
                requireNotNull(
                    repository.getItem(
                        secondParentId
                    )
                )

            assertEquals(
                240.0,
                secondParent.caloriesPer100g,
                TOLERANCE,
            )

            assertEquals(
                16.0,
                secondParent.proteinPer100g,
                TOLERANCE,
            )
        }

    @Test
    fun invalidComponentsAreRejectedWithoutParent(): Unit =
        runBlocking {
            val firstId =
                repository.createItem(
                    itemDraft(name = "First")
                )

            val secondId =
                repository.createItem(
                    itemDraft(name = "Second")
                )

            val invalidComponents =
                listOf(
                    emptyList(),
                    listOf(
                        NutritionComponentDraft(
                            itemId = firstId,
                            gramsPer100g = 50.0,
                        ),
                        NutritionComponentDraft(
                            itemId = firstId,
                            gramsPer100g = 50.0,
                        ),
                    ),
                    listOf(
                        NutritionComponentDraft(
                            itemId = firstId,
                            gramsPer100g = 0.0,
                        ),
                        NutritionComponentDraft(
                            itemId = secondId,
                            gramsPer100g = 100.0,
                        ),
                    ),
                    listOf(
                        NutritionComponentDraft(
                            itemId = firstId,
                            gramsPer100g =
                                Double.NaN,
                        )
                    ),
                    listOf(
                        NutritionComponentDraft(
                            itemId = firstId,
                            gramsPer100g = 60.0,
                        ),
                        NutritionComponentDraft(
                            itemId = secondId,
                            gramsPer100g = 39.0,
                        ),
                    ),
                )

            invalidComponents.forEachIndexed {
                    index,
                    components,
                ->
                assertComposedCreationFails(
                    ComposedNutritionItemDraft(
                        name = "Invalid $index",
                        components = components,
                    )
                )
            }

            assertEquals(
                listOf("First", "Second"),
                repository.observeAllItems()
                    .first()
                    .map { it.name },
            )
        }

    @Test
    fun missingComponentRollsBackCreation(): Unit =
        runBlocking {
            assertComposedCreationFails(
                ComposedNutritionItemDraft(
                    name = "Missing parent",
                    components = listOf(
                        NutritionComponentDraft(
                            itemId = Long.MAX_VALUE,
                            gramsPer100g = 100.0,
                        )
                    ),
                )
            )

            assertTrue(
                repository.observeAllItems()
                    .first()
                    .isEmpty()
            )
        }

    @Test
    fun archivedComponentRollsBackCreation(): Unit =
        runBlocking {
            val componentId =
                repository.createItem(
                    itemDraft(
                        name = "Archived component"
                    )
                )

            val existingParentId =
                repository.createComposedItem(
                    ComposedNutritionItemDraft(
                        name = "Existing parent",
                        components = listOf(
                            NutritionComponentDraft(
                                itemId = componentId,
                                gramsPer100g =
                                    100.0,
                            )
                        ),
                    )
                )

            assertEquals(
                1,
                dao.archiveReferencedItem(
                    itemId = componentId,
                    timestampMillis =
                        SECOND_TIME,
                ),
            )

            assertComposedCreationFails(
                ComposedNutritionItemDraft(
                    name = "Rejected parent",
                    components = listOf(
                        NutritionComponentDraft(
                            itemId = componentId,
                            gramsPer100g =
                                100.0,
                        )
                    ),
                )
            )

            assertEquals(
                setOf(
                    componentId,
                    existingParentId,
                ),
                repository.observeAllItems()
                    .first()
                    .map { it.id }
                    .toSet(),
            )
        }

    @Test
    fun manualUpdateRemovesComponentsAndRecalculatesParents(): Unit =
        runBlocking {
            val baseId =
                repository.createItem(
                    itemDraft(
                        name = "Base",
                        calories = 100.0,
                        protein = 10.0,
                    )
                )

            val middleId =
                repository.createComposedItem(
                    composedDraft(
                        name = "Middle",
                        baseId to 100.0,
                    )
                )

            val topId =
                repository.createComposedItem(
                    composedDraft(
                        name = "Top",
                        middleId to 100.0,
                    )
                )

            assertTrue(
                repository.updateItem(
                    itemId = middleId,
                    draft =
                        itemDraft(
                            name = "Middle",
                            calories = 250.0,
                            protein = 25.0,
                        ),
                    timestampMillis =
                        SECOND_TIME,
                )
            )

            assertTrue(
                dao.getComponents(middleId)
                    .isEmpty()
            )

            assertEquals(
                0,
                dao.countIncomingReferences(
                    baseId
                ),
            )

            val middle =
                requireNotNull(
                    repository.getItem(middleId)
                )

            val top =
                requireNotNull(
                    repository.getItem(topId)
                )

            assertEquals(
                250.0,
                middle.caloriesPer100g,
                TOLERANCE,
            )

            assertEquals(
                25.0,
                middle.proteinPer100g,
                TOLERANCE,
            )

            assertEquals(
                250.0,
                top.caloriesPer100g,
                TOLERANCE,
            )

            assertEquals(
                25.0,
                top.proteinPer100g,
                TOLERANCE,
            )
        }

    @Test
    fun composedUpdateReplacesComponentsAndRecalculatesParents(): Unit =
        runBlocking {
            val firstId =
                repository.createItem(
                    itemDraft(
                        name = "First",
                        calories = 100.0,
                        protein = 10.0,
                    )
                )

            val secondId =
                repository.createItem(
                    itemDraft(
                        name = "Second",
                        calories = 300.0,
                        protein = 30.0,
                    )
                )

            val middleId =
                repository.createComposedItem(
                    composedDraft(
                        name = "Middle",
                        firstId to 100.0,
                    )
                )

            val topId =
                repository.createComposedItem(
                    composedDraft(
                        name = "Top",
                        middleId to 100.0,
                    )
                )

            assertTrue(
                repository.updateComposedItem(
                    itemId = middleId,
                    draft =
                        composedDraft(
                            name = "Middle",
                            firstId to 25.0,
                            secondId to 75.0,
                        ),
                    timestampMillis =
                        SECOND_TIME,
                )
            )

            val components =
                dao.getComponents(middleId)

            assertEquals(
                listOf(firstId, secondId),
                components.map {
                    it.componentItemId
                },
            )

            assertEquals(
                listOf(0, 1),
                components.map {
                    it.displayOrder
                },
            )

            val middle =
                requireNotNull(
                    repository.getItem(middleId)
                )

            val top =
                requireNotNull(
                    repository.getItem(topId)
                )

            assertEquals(
                250.0,
                middle.caloriesPer100g,
                TOLERANCE,
            )

            assertEquals(
                25.0,
                middle.proteinPer100g,
                TOLERANCE,
            )

            assertEquals(
                250.0,
                top.caloriesPer100g,
                TOLERANCE,
            )

            assertEquals(
                25.0,
                top.proteinPer100g,
                TOLERANCE,
            )

            assertEquals(
                SECOND_TIME,
                top.updatedAtEpochMillis,
            )
        }

    @Test
    fun componentCyclesAreRejectedWithoutChanges(): Unit =
        runBlocking {
            val baseId =
                repository.createItem(
                    itemDraft(
                        name = "Base",
                        calories = 100.0,
                    )
                )

            val middleId =
                repository.createComposedItem(
                    composedDraft(
                        name = "Middle",
                        baseId to 100.0,
                    )
                )

            val topId =
                repository.createComposedItem(
                    composedDraft(
                        name = "Top",
                        middleId to 100.0,
                    )
                )

            val directFailure =
                runCatching {
                    repository.updateComposedItem(
                        itemId = middleId,
                        draft =
                            composedDraft(
                                name = "Middle",
                                middleId to 100.0,
                            ),
                    )
                }

            assertNotNull(
                directFailure.exceptionOrNull()
            )

            assertEquals(
                listOf(baseId),
                dao.getComponents(middleId)
                    .map { it.componentItemId },
            )

            val indirectFailure =
                runCatching {
                    repository.updateComposedItem(
                        itemId = baseId,
                        draft =
                            composedDraft(
                                name = "Base",
                                topId to 100.0,
                            ),
                    )
                }

            assertNotNull(
                indirectFailure.exceptionOrNull()
            )

            assertTrue(
                dao.getComponents(baseId)
                    .isEmpty()
            )

            assertEquals(
                100.0,
                requireNotNull(
                    repository.getItem(baseId)
                ).caloriesPer100g,
                TOLERANCE,
            )
        }

    @Test
    fun composedSaveAsCreatesIndependentVersion(): Unit =
        runBlocking {
            val firstId =
                repository.createItem(
                    itemDraft(
                        name = "First",
                        calories = 100.0,
                        protein = 10.0,
                    )
                )

            val secondId =
                repository.createItem(
                    itemDraft(
                        name = "Second",
                        calories = 300.0,
                        protein = 30.0,
                    )
                )

            val originalId =
                repository.createComposedItem(
                    composedDraft(
                        name = "Blend",
                        firstId to 100.0,
                    )
                )

            val newId =
                requireNotNull(
                    repository
                        .saveComposedAsVersion(
                            itemId = originalId,
                            draft =
                                composedDraft(
                                    name = "Blend",
                                    firstId to 50.0,
                                    secondId to 50.0,
                                ),
                            timestampMillis =
                                SECOND_TIME,
                        )
                )

            val versions =
                repository.getVersions("Blend")

            assertEquals(
                listOf(0, 1),
                versions.map { it.version },
            )

            assertEquals(
                listOf(firstId),
                dao.getComponents(originalId)
                    .map { it.componentItemId },
            )

            assertEquals(
                listOf(firstId, secondId),
                dao.getComponents(newId)
                    .map { it.componentItemId },
            )

            assertEquals(
                100.0,
                requireNotNull(
                    repository.getItem(originalId)
                ).caloriesPer100g,
                TOLERANCE,
            )

            assertEquals(
                200.0,
                requireNotNull(
                    repository.getItem(newId)
                ).caloriesPer100g,
                TOLERANCE,
            )
        }

    @Test
    fun existingArchivedComponentCanBeRetained(): Unit =
        runBlocking {
            val archivedId =
                repository.createItem(
                    itemDraft(
                        name = "Archived",
                        calories = 200.0,
                        protein = 20.0,
                    )
                )

            val otherId =
                repository.createItem(
                    itemDraft(
                        name = "Other",
                        calories = 100.0,
                        protein = 10.0,
                    )
                )

            val originalId =
                repository.createComposedItem(
                    composedDraft(
                        name = "Original",
                        archivedId to 100.0,
                    )
                )

            assertEquals(
                1,
                dao.archiveReferencedItem(
                    itemId = archivedId,
                    timestampMillis =
                        SECOND_TIME,
                ),
            )

            assertTrue(
                repository.updateComposedItem(
                    itemId = originalId,
                    draft =
                        composedDraft(
                            name = "Original",
                            archivedId to 100.0,
                        ),
                    timestampMillis =
                        THIRD_TIME,
                )
            )

            val newVersionId =
                repository.saveComposedAsVersion(
                    itemId = originalId,
                    draft =
                        composedDraft(
                            name = "Original",
                            archivedId to 100.0,
                        ),
                    timestampMillis =
                        THIRD_TIME,
                )

            assertNotNull(newVersionId)

            val unrelatedParentId =
                repository.createComposedItem(
                    composedDraft(
                        name = "Unrelated parent",
                        otherId to 100.0,
                    )
                )

            val addingArchivedFailure =
                runCatching {
                    repository.updateComposedItem(
                        itemId =
                            unrelatedParentId,
                        draft =
                            composedDraft(
                                name =
                                    "Unrelated parent",
                                archivedId to 50.0,
                                otherId to 50.0,
                            ),
                    )
                }

            assertNotNull(
                addingArchivedFailure
                    .exceptionOrNull()
            )

            assertEquals(
                listOf(otherId),
                dao.getComponents(
                    unrelatedParentId
                )
                    .map { it.componentItemId },
            )
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

    private fun composedDraft(
        name: String,
        vararg components:
        Pair<Long, Double>,
    ): ComposedNutritionItemDraft =
        ComposedNutritionItemDraft(
            name = name,
            components =
                components.map {
                        (itemId, grams) ->
                    NutritionComponentDraft(
                        itemId = itemId,
                        gramsPer100g = grams,
                    )
                },
        )

    private suspend fun assertComposedCreationFails(
        draft: ComposedNutritionItemDraft,
    ) {
        val before =
            repository.observeAllItems()
                .first()
                .map { it.id }
                .toSet()

        val failure =
            runCatching {
                repository.createComposedItem(
                    draft
                )
            }

        assertNotNull(
            failure.exceptionOrNull()
        )

        val after =
            repository.observeAllItems()
                .first()
                .map { it.id }
                .toSet()

        assertEquals(before, after)
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
        const val THIRD_TIME = 3_000L
    }
}