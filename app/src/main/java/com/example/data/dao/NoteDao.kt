package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM smart_notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM smart_notes WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    suspend fun searchNotes(query: String): List<NoteEntity>

    @Query("SELECT * FROM smart_notes ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentNotes(limit: Int): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM smart_notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM smart_notes")
    suspend fun clearAll()
}
