package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.ChatDao
import com.example.data.dao.MemoryDao
import com.example.data.dao.NoteDao
import com.example.data.dao.ReminderDao
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryEntity
import com.example.data.model.NoteEntity
import com.example.data.model.ReminderEntity

@Database(
    entities = [
        ChatMessage::class,
        NoteEntity::class,
        MemoryEntity::class,
        ReminderEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun noteDao(): NoteDao
    abstract fun memoryDao(): MemoryDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "snow_ai_database"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
