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

    @Query("UPDATE hosts SET lastConnected = :ts WHERE id = :id")
    suspend fun updateLastConnected(id: Long, ts: Long)

    @Query("UPDATE hosts SET groupId = NULL WHERE groupId = :groupId")
    suspend fun clearGroup(groupId: Long)
}
