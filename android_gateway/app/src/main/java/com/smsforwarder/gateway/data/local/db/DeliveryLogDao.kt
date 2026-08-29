package com.smsforwarder.gateway.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryLogDao {
    @Insert
    suspend fun insert(entry: DeliveryLogEntity)

    // limit has a Kotlin default (personal-scale usage, see spec 0017) - Room only
    // needs the SQL parameter, callers get the default via ordinary Kotlin resolution.
    @Query("SELECT * FROM delivery_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<DeliveryLogEntity>>
}
