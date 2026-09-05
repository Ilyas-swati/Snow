package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM agent_memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM agent_memories ORDER BY timestamp DESC")
    suspend fun getAllMemoriesList(): List<MemoryEntity>

    @Query("SELECT * FROM agent_memories WHERE memoryText LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity): Long

    @Query("DELETE FROM agent_memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM agent_memories WHERE memoryText LIKE '%' || :query || '%'")
    suspend fun deleteMatching(query: String): Int

    @Query("DELETE FROM agent_memories")
    suspend fun clearAll()
}
