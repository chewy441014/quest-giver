package com.prestonhill.questgiver.data.local.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.prestonhill.questgiver.data.local.database.entity.FoodLogEntity
import com.prestonhill.questgiver.data.local.database.entity.NutritionComponentEntity
import com.prestonhill.questgiver.data.local.database.entity.NutritionItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NutritionDao {
    @Query(
        """
        SELECT * FROM nutrition_items
        WHERE archivedAtEpochMillis IS NULL
        ORDER BY nameKey, version
        """
    )
    fun observeActiveItems():
            Flow<List<NutritionItemEntity>>

    @Query(
        """
        SELECT * FROM nutrition_items
        ORDER BY nameKey, version
        """
    )
    fun observeAllItems():
            Flow<List<NutritionItemEntity>>

    @Query(
        """
        SELECT * FROM nutrition_items
        WHERE archivedAtEpochMillis IS NOT NULL
        ORDER BY archivedAtEpochMillis DESC, id DESC
        """
    )
    fun observeArchivedItems():
            Flow<List<NutritionItemEntity>>

    @Query(
        """
        SELECT * FROM nutrition_items
        WHERE id = :itemId
        LIMIT 1
        """
    )
    suspend fun getItem(
        itemId: Long,
    ): NutritionItemEntity?

    @Query(
        """
        SELECT * FROM nutrition_items
        WHERE nameKey = :nameKey
        ORDER BY version
        """
    )
    suspend fun getVersions(
        nameKey: String,
    ): List<NutritionItemEntity>

    @Query(
        """
        SELECT COALESCE(MAX(version), -1)
        FROM nutrition_items
        WHERE nameKey = :nameKey
        """
    )
    suspend fun getMaxVersion(
        nameKey: String,
    ): Int

    @Insert(
        onConflict =
            OnConflictStrategy.ABORT
    )
    suspend fun insertItem(
        item: NutritionItemEntity,
    ): Long

    @Update
    suspend fun updateItem(
        item: NutritionItemEntity,
    ): Int

    @Query(
        """
        SELECT * FROM nutrition_components
        WHERE parentItemId = :parentItemId
        ORDER BY displayOrder, componentItemId
        """
    )
    suspend fun getComponents(
        parentItemId: Long,
    ): List<NutritionComponentEntity>

    @Query(
        """
        SELECT parentItemId
        FROM nutrition_components
        WHERE componentItemId = :componentItemId
        ORDER BY parentItemId
        """
    )
    suspend fun getParentIds(
        componentItemId: Long,
    ): List<Long>

    @Query(
        """
        SELECT COUNT(*)
        FROM nutrition_components
        WHERE componentItemId = :itemId
        """
    )
    suspend fun countIncomingReferences(
        itemId: Long,
    ): Int

    @Insert(
        onConflict =
            OnConflictStrategy.ABORT
    )
    suspend fun insertComponents(
        components:
        List<NutritionComponentEntity>,
    )

    @Query(
        """
        DELETE FROM nutrition_components
        WHERE parentItemId = :parentItemId
        """
    )
    suspend fun deleteComponents(
        parentItemId: Long,
    ): Int

    @Query(
        """
        UPDATE nutrition_items
        SET archivedAtEpochMillis =
                :timestampMillis,
            updatedAtEpochMillis =
                :timestampMillis
        WHERE id = :itemId
          AND archivedAtEpochMillis IS NULL
          AND EXISTS (
              SELECT 1
              FROM nutrition_components
              WHERE componentItemId = :itemId
          )
        """
    )
    suspend fun archiveReferencedItem(
        itemId: Long,
        timestampMillis: Long,
    ): Int

    @Query(
        """
        UPDATE nutrition_items
        SET archivedAtEpochMillis = NULL,
            updatedAtEpochMillis =
                :timestampMillis
        WHERE id = :itemId
          AND archivedAtEpochMillis IS NOT NULL
        """
    )
    suspend fun restoreItem(
        itemId: Long,
        timestampMillis: Long,
    ): Int

    @Query(
        """
        DELETE FROM nutrition_items
        WHERE id = :itemId
          AND NOT EXISTS (
              SELECT 1
              FROM nutrition_components
              WHERE componentItemId = :itemId
          )
        """
    )
    suspend fun deleteUnreferencedItem(
        itemId: Long,
    ): Int

    @Query(
        """
        SELECT * FROM food_logs
        WHERE consumedAtEpochMillis >=
                :startTimestampMillis
          AND consumedAtEpochMillis <
                :endTimestampMillis
        ORDER BY consumedAtEpochMillis, id
        """
    )
    fun observeLogsBetween(
        startTimestampMillis: Long,
        endTimestampMillis: Long,
    ): Flow<List<FoodLogEntity>>

    @Query(
        """
        SELECT * FROM food_logs
        ORDER BY consumedAtEpochMillis, id
        """
    )
    fun observeAllLogs():
            Flow<List<FoodLogEntity>>

    @Query(
        """
        SELECT * FROM food_logs
        WHERE id = :logId
        LIMIT 1
        """
    )
    suspend fun getLog(
        logId: Long,
    ): FoodLogEntity?

    @Insert(
        onConflict =
            OnConflictStrategy.ABORT
    )
    suspend fun insertLog(
        log: FoodLogEntity,
    ): Long

    @Update
    suspend fun updateLog(
        log: FoodLogEntity,
    ): Int

    @Query(
        """
        DELETE FROM food_logs
        WHERE id = :logId
        """
    )
    suspend fun deleteLog(
        logId: Long,
    ): Int

    @Query(
        """
    SELECT * FROM nutrition_items
    WHERE id IN (:itemIds)
    """
    )
    suspend fun getItemsByIds(
        itemIds: List<Long>,
    ): List<NutritionItemEntity>

    @Query(
        """
    SELECT * FROM nutrition_components
    ORDER BY parentItemId, displayOrder
    """
    )
    suspend fun getAllComponents():
            List<NutritionComponentEntity>
}