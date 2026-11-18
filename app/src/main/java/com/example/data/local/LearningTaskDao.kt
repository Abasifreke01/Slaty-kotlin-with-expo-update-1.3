package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningTaskDao {

    @Query("SELECT * FROM learning_tasks WHERE studentId = :studentId AND isDeletedLocally = 0 ORDER BY dueDateMillis ASC")
    fun getTasksForStudentFlow(studentId: String): Flow<List<LearningTaskEntity>>

    @Query("SELECT * FROM learning_tasks WHERE studentId = :studentId AND status != 'COMPLETED' AND isDeletedLocally = 0 ORDER BY dueDateMillis ASC")
    fun getActiveTasksForStudentFlow(studentId: String): Flow<List<LearningTaskEntity>>

    @Query("SELECT * FROM learning_tasks WHERE studentId = :studentId AND status = 'COMPLETED' AND isDeletedLocally = 0 ORDER BY updatedAtMillis DESC")
    fun getCompletedTasksForStudentFlow(studentId: String): Flow<List<LearningTaskEntity>>

    @Query("SELECT * FROM learning_tasks WHERE id = :taskId AND isDeletedLocally = 0 LIMIT 1")
    fun getTaskByIdFlow(taskId: String): Flow<LearningTaskEntity?>

    @Query("SELECT * FROM learning_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: String): LearningTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(task: LearningTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<LearningTaskEntity>)

    @Query("UPDATE learning_tasks SET progressPercentage = :progress, status = :status, completedAtMillis = :completedAt, updatedAtMillis = :updatedAt WHERE id = :taskId")
    suspend fun updateTaskProgress(
        taskId: String,
        progress: Int,
        status: String,
        completedAt: Long? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE learning_tasks SET isDeletedLocally = 1, syncPending = 1, updatedAtMillis = :updatedAt WHERE id = :taskId")
    suspend fun markTaskDeleted(taskId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM learning_tasks WHERE id = :taskId")
    suspend fun hardDeleteTask(taskId: String)

    @Query("SELECT * FROM learning_tasks WHERE syncPending = 1")
    suspend fun getPendingSyncTasks(): List<LearningTaskEntity>
}
