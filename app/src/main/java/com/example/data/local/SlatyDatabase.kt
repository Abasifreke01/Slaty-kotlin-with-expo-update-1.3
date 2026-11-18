package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.TypeConverters

@Database(
    entities = [
        TaskEntity::class,
        StudentTaskEntity::class,
        StudentProfileEntity::class,
        TutorSessionEntity::class,
        LearningTaskEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(SlatyTypeConverters::class)
abstract class SlatyDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun studentTaskDao(): StudentTaskDao
    abstract fun studentProfileDao(): StudentProfileDao
    abstract fun tutorSessionDao(): TutorSessionDao
    abstract fun learningTaskDao(): LearningTaskDao

    companion object {
        @Volatile
        private var INSTANCE: SlatyDatabase? = null

        fun getInstance(context: Context): SlatyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SlatyDatabase::class.java,
                    "slaty_tasks.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
