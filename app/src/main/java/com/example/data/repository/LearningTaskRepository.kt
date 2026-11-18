package com.example.data.repository

import android.util.Log
import com.example.data.local.LearningTaskDao
import com.example.data.local.LearningTaskEntity
import com.example.model.LearningTask
import com.example.model.LearningTaskPriority
import com.example.model.LearningTaskStatus
import com.example.model.LearningTaskType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class LearningTaskRepository(
    private val learningTaskDao: LearningTaskDao,
    private val firestore: FirebaseFirestore?,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var activeTasksListener: ListenerRegistration? = null

    /**
     * Observes all learning tasks for a student from local Room database.
     * Starts a background Firestore snapshot listener to keep Room up to date.
     */
    fun observeTasks(studentId: String): Flow<List<LearningTask>> {
        attachFirestoreListener(studentId)
        return learningTaskDao.getTasksForStudentFlow(studentId).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    fun observeActiveTasks(studentId: String): Flow<List<LearningTask>> {
        attachFirestoreListener(studentId)
        return learningTaskDao.getActiveTasksForStudentFlow(studentId).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    fun observeCompletedTasks(studentId: String): Flow<List<LearningTask>> {
        attachFirestoreListener(studentId)
        return learningTaskDao.getCompletedTasksForStudentFlow(studentId).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    suspend fun createTask(task: LearningTask): Result<Unit> {
        val now = System.currentTimeMillis()
        val prepared = if (task.id.isBlank()) {
            task.copy(id = "ltask_${UUID.randomUUID().toString().take(8)}", createdAtMillis = now, updatedAtMillis = now)
        } else {
            task.copy(createdAtMillis = now, updatedAtMillis = now)
        }

        // 1. Cache immediately in Room
        learningTaskDao.insertOrUpdate(
            LearningTaskEntity.fromDomainModel(prepared, syncPending = firestore == null)
        )

        // 2. Synchronize to Firestore
        if (firestore != null) {
            return try {
                firestore.collection("learning_tasks")
                    .document(prepared.id)
                    .set(prepared.toFirestoreMap(), SetOptions.merge())
                    .await()

                learningTaskDao.insertOrUpdate(
                    LearningTaskEntity.fromDomainModel(prepared, syncPending = false)
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Log.w("LearningTaskRepo", "Firestore sync error for task: ${e.message}")
                Result.failure(e)
            }
        }
        return Result.success(Unit)
    }

    suspend fun updateTaskProgress(taskId: String, progress: Int): Result<Unit> {
        val bounded = progress.coerceIn(0, 100)
        val now = System.currentTimeMillis()
        val newStatus = when {
            bounded >= 100 -> LearningTaskStatus.COMPLETED
            bounded > 0 -> LearningTaskStatus.IN_PROGRESS
            else -> LearningTaskStatus.TODO
        }
        val completedAt = if (newStatus == LearningTaskStatus.COMPLETED) now else null

        learningTaskDao.updateTaskProgress(taskId, bounded, newStatus.name, completedAt, now)

        if (firestore != null) {
            return try {
                firestore.collection("learning_tasks")
                    .document(taskId)
                    .set(
                        mapOf(
                            "progressPercentage" to bounded,
                            "status" to newStatus.name,
                            "completedAtMillis" to completedAt,
                            "updatedAtMillis" to now
                        ),
                        SetOptions.merge()
                    )
                    .await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        return Result.success(Unit)
    }

    suspend fun toggleTaskComplete(taskId: String, isCompleted: Boolean): Result<Unit> {
        return updateTaskProgress(taskId, if (isCompleted) 100 else 0)
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        val now = System.currentTimeMillis()
        learningTaskDao.markTaskDeleted(taskId, now)

        if (firestore != null) {
            return try {
                firestore.collection("learning_tasks")
                    .document(taskId)
                    .delete()
                    .await()
                learningTaskDao.hardDeleteTask(taskId)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            learningTaskDao.hardDeleteTask(taskId)
        }
        return Result.success(Unit)
    }

    private fun attachFirestoreListener(studentId: String) {
        if (firestore == null) return
        activeTasksListener?.remove()

        try {
            activeTasksListener = firestore.collection("learning_tasks")
                .whereEqualTo("studentId", studentId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("LearningTaskRepo", "Snapshot error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val tasks = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { LearningTask.fromFirestoreMap(doc.id, it) }
                        }
                        scope.launch {
                            learningTaskDao.insertAll(
                                tasks.map { LearningTaskEntity.fromDomainModel(it, syncPending = false) }
                            )
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w("LearningTaskRepo", "Error attaching learning tasks listener: ${e.message}")
        }
    }

    fun cleanup() {
        activeTasksListener?.remove()
        activeTasksListener = null
    }
}
