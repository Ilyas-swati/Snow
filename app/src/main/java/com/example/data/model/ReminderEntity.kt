package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val targetTimeMillis: Long,
    val isCompleted: Boolean = false,
    val type: String = "REMINDER", // "REMINDER", "ALARM", "TIMER"
    val createdAt: Long = System.currentTimeMillis()
)
