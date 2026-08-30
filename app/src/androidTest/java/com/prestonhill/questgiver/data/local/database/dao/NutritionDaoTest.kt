package com.prestonhill.questgiver.data.local.database.dao

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.local.database.entity.FoodLogEntity
import com.prestonhill.questgiver.data.local.database.entity.NutritionComponentEntity
import com.prestonhill.questgiver.data.local.database.entity.NutritionItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NutritionDaoTest {
    private lateinit var database:
            QuestGiverDatabase

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
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun nameAndVersionAreUnique(): Unit =
        runBlocking {
            addItem(
                name = "Oats",
                nameKey = "oats",
                version = 0,
            )

            val duplicate =
                runCatching {
                    addItem(
                        name = "OATS",
                        nameKey = "oats",
                        version = 0,
                    )
                }

            assertNotNull(
                duplicate.exceptionOrNull()
            )

            addItem(
                name = "Oats",
                nameKey = "oats",
                version = 1,
            )

            assertEquals(
                listOf(0, 1),
                dao.getVersions("oats")
                    .map { it.version },
            )
        }

    @Test
    fun unreferencedItemCannotBeArchived(): Unit =
        runBlocking {
            val itemId = addItem()

            assertEquals(
                0,
                dao.archiveReferencedItem(
                    itemId = itemId,
                    timestampMillis =
                        ARCHIVE_TIME,
                ),
            )

            val item = dao.getItem(itemId)

            assertNotNull(item)

            assertNull(
                item?.archivedAtEpochMillis
            )
        }

    @Test
    fun referencedItemCanOnlyBeArchived(): Unit =
        runBlocking {
            val componentId =
                addItem(
                    name = "Chicken",
                    nameKey = "chicken",
                )

            val parentId =
                addItem(
                    name = "Chicken bowl",
                    nameKey = "chicken bowl",
                )

            dao.insertComponents(
                listOf(
                    NutritionComponentEntity(
                        parentItemId = parentId,
                        componentItemId =
                            componentId,
                        gramsPer100g = 100.0,
                    )
                )
            )

            assertEquals(
                1,
                dao.countIncomingReferences(
                    componentId
                ),
            )

            assertEquals(
                0,
                dao.deleteUnreferencedItem(
                    componentId
                ),
            )

            assertNotNull(
                dao.getItem(componentId)
            )

            assertEquals(
                1,
                dao.archiveReferencedItem(
                    itemId = componentId,
                    timestampMillis =
                        ARCHIVE_TIME,
                ),
            )

            assertEquals(
                ARCHIVE_TIME,
                dao.getItem(componentId)
                    ?.archivedAtEpochMillis,
            )

            // Deleting the parent cascades its
            // outgoing component relationship.
            assertEquals(
                1,
                dao.deleteUnreferencedItem(
                    parentId
                ),
            )

            assertEquals(
                0,
                dao.countIncomingReferences(
                    componentId
                ),
            )

            assertNotNull(
                dao.getItem(componentId)
            )

            // Once no parent references remain,
            // the archived component is deletable.
            assertEquals(
                1,
                dao.deleteUnreferencedItem(
                    componentId
                ),
            )

            assertNull(
                dao.getItem(componentId)
            )
        }

    @Test
    fun itemDeletionCascadesLogs(): Unit =
        runBlocking {
            val itemId = addItem()

            addLog(
                itemId = itemId,
                consumedAt = 1_000L,
            )

            addLog(
                itemId = itemId,
                consumedAt = 2_000L,
            )

            assertEquals(
                2,
                dao.observeAllLogs()
                    .first()
                    .size,
            )

            assertEquals(
                1,
                dao.deleteUnreferencedItem(
                    itemId
                ),
            )

            assertNull(
                dao.getItem(itemId)
            )

            assertTrue(
                dao.observeAllLogs()
                    .first()
                    .isEmpty()
            )
        }

    @Test
    fun logRangeIsHalfOpenAndOrdered(): Unit =
        runBlocking {
            val itemId = addItem()

            addLog(
                itemId = itemId,
                consumedAt = 99L,
            )

            val firstIncluded =
                addLog(
                    itemId = itemId,
                    consumedAt = 100L,
                )

            val secondIncluded =
                addLog(
                    itemId = itemId,
                    consumedAt = 199L,
                )

            addLog(
                itemId = itemId,
                consumedAt = 200L,
            )

            val logs =
                dao.observeLogsBetween(
                    startTimestampMillis = 100L,
                    endTimestampMillis = 200L,
                )
                    .first()

            assertEquals(
                listOf(
                    firstIncluded,
                    secondIncluded,
                ),
                logs.map { it.id },
            )
        }

    private suspend fun addItem(
        name: String = "Test item",
        nameKey: String = "test item",
        version: Int = 0,
    ): Long =
        dao.insertItem(
            NutritionItemEntity(
                name = name,
                nameKey = nameKey,
                version = version,
                caloriesPer100g = 100.0,
                proteinPer100g = 10.0,
                createdAtEpochMillis =
                    CREATED_TIME,
                updatedAtEpochMillis =
                    CREATED_TIME,
            )
        )

    private suspend fun addLog(
        itemId: Long,
        consumedAt: Long,
    ): Long =
        dao.insertLog(
            FoodLogEntity(
                itemId = itemId,
                consumedAtEpochMillis =
                    consumedAt,
                weightGrams = 100.0,
                createdAtEpochMillis =
                    consumedAt,
                updatedAtEpochMillis =
                    consumedAt,
            )
        )

    private companion object {
        const val CREATED_TIME = 1_000L
        const val ARCHIVE_TIME = 2_000L
    }
}