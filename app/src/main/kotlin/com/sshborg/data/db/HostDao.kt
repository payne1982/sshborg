package com.sshborg.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
    @Query("SELECT * FROM hosts ORDER BY label ASC")
    fun getAll(): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts")
    suspend fun getAllOnce(): List<HostEntity>

    @Query("SELECT * FROM hosts WHERE id = :id")
    suspend fun getById(id: Long): HostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(host: HostEntity): Long

    @Delete
    suspend fun delete(host: HostEntity)

    /** One connection: stamps the time and bumps the counter the "most used" order reads. */
    @Query("UPDATE hosts SET lastConnected = :ts, connectCount = connectCount + 1 WHERE id = :id")
    suspend fun recordConnection(id: Long, ts: Long)

    @Query("UPDATE hosts SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Query("UPDATE hosts SET groupId = NULL WHERE groupId = :groupId")
    suspend fun clearGroup(groupId: Long)
}
