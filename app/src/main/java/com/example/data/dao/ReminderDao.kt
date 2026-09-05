package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY targetTimeMillis ASC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY targetTimeMillis ASC")
    suspend fun getActiveReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE targetTimeMillis >= :startTime AND targetTimeMillis <= :endTime ORDER BY targetTimeMillis ASC")
    suspend fun getRemindersInRange(startTime: Long, endTime: Long): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reminders")
    suspend fun clearAll()
}
