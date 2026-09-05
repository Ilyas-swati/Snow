package com.example.agent

import com.example.data.dao.MemoryDao
import com.example.data.model.MemoryEntity
import kotlinx.coroutines.flow.Flow

class MemoryManager(private val memoryDao: MemoryDao) {

    fun getAllMemories(): Flow<List<MemoryEntity>> = memoryDao.getAllMemories()

    suspend fun saveMemory(text: String, category: String = "general"): String {
        val clean = text.trim()
        if (clean.isBlank()) return "Memory text cannot be empty."
        val id = memoryDao.insert(MemoryEntity(category = category, memoryText = clean))
        return "Saved memory #$id: '$clean'"
    }

    suspend fun recallMemory(query: String): String {
        val memories = if (query.isBlank()) {
            memoryDao.getAllMemoriesList()
        } else {
            memoryDao.searchMemories(query)
        }
        if (memories.isEmpty()) {
            return "No memories found matching '$query'."
        }
        return memories.take(5).joinToString("\n") { "• ${it.memoryText}" }
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteById(id)
    }

    suspend fun forgetMatching(query: String): String {
        val count = memoryDao.deleteMatching(query)
        return if (count > 0) "Deleted $count matching memories." else "No memories matched '$query'."
    }

    suspend fun clearAll() {
        memoryDao.clearAll()
    }

    suspend fun getFormattedMemoriesForContext(): String {
        val list = memoryDao.getAllMemoriesList().take(10)
        if (list.isEmpty()) return ""
        return "STORED USER MEMORIES:\n" + list.joinToString("\n") { "- ${it.memoryText}" }
    }
}
