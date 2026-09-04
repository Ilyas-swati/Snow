package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a chat conversation turn between user and Snow AI.
 */
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String, // "user" or "snow"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val language: String = "English",
    val imageUri: String? = null,
    val actionSummary: String? = null
)
