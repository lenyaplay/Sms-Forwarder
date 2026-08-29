package com.smsforwarder.gateway.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: FilterRuleEntity): Long

    @Query("SELECT * FROM filter_rules WHERE id = :id")
    suspend fun getById(id: Long): FilterRuleEntity?

    @Query("SELECT * FROM filter_rules WHERE stage = :stage ORDER BY sortOrder ASC")
    fun observeRules(stage: FilterStage): Flow<List<FilterRuleEntity>>

    @Query("DELETE FROM filter_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
