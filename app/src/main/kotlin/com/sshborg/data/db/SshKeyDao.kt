package com.sshborg.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SshKeyDao {
    @Query("SELECT * FROM ssh_keys ORDER BY label ASC")
    fun getAll(): Flow<List<SshKeyEntity>>

    @Query("SELECT * FROM ssh_keys WHERE id = :id")
    suspend fun getById(id: Long): SshKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(key: SshKeyEntity): Long

    @Delete
    suspend fun delete(key: SshKeyEntity)
}
