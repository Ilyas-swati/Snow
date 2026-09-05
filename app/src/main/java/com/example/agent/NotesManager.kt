package com.example.agent

import com.example.data.dao.NoteDao
import com.example.data.model.NoteEntity
import kotlinx.coroutines.flow.Flow

class NotesManager(private val noteDao: NoteDao) {

    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()

    suspend fun saveNote(title: String, content: String, tag: String = "general"): String {
        val effectiveTitle = title.ifBlank { "Quick Note" }
        val id = noteDao.insert(
            NoteEntity(
                title = effectiveTitle,
                content = content,
                tag = tag
            )
        )
        return "Saved note '$effectiveTitle' (ID: $id)"
    }

    suspend fun searchNotes(query: String): String {
        val results = noteDao.searchNotes(query)
        if (results.isEmpty()) {
            return "No notes found matching '$query'."
        }
        return results.take(4).joinToString("\n\n") { "• ${it.title}: ${it.content}" }
    }

    suspend fun getRecentNotesSummary(): String {
        val notes = noteDao.getRecentNotes(3)
        if (notes.isEmpty()) return "No recent notes."
        return notes.joinToString("; ") { "${it.title}: ${it.content.take(50)}" }
    }

    suspend fun deleteNote(id: Long) {
        noteDao.deleteById(id)
    }

    suspend fun clearAll() {
        noteDao.clearAll()
    }
}
