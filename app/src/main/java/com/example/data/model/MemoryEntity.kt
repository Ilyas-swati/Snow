package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String = "general", // e.g., "personal", "preference", "fact", "work"
    val memoryText: String,
    val timestamp: Long = System.currentTimeMillis()
)
