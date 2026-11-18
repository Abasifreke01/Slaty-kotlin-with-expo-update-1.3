package com.example.data.repository

import android.util.Log
import com.example.data.local.TutorSessionDao
import com.example.data.local.TutorSessionEntity
import com.example.model.TutorSession
import com.example.model.TutorSessionStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TutorSessionRepository(
    private val tutorSessionDao: TutorSessionDao,
    private val firestore: FirebaseFirestore?,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var activeSessionsListener: ListenerRegistration? = null

    /**
     * Observes all tutor sessions for a student reactively from Room.
     * Keeps Room cache populated via Firestore real-time listener.
     */
    fun observeStudentSessions(studentId: String): Flow<List<TutorSession>> {
        attachStudentSessionsListener(studentId)
        return tutorSessionDao.getSessionsForStudentFlow(studentId).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    fun observeUpcomingSessions(studentId: String): Flow<List<TutorSession>> {
        attachStudentSessionsListener(studentId)
        return tutorSessionDao.getUpcomingSessionsForStudentFlow(studentId).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    fun observeTutorSessions(tutorId: String): Flow<List<TutorSession>> {
        attachTutorSessionsListener(tutorId)
        return tutorSessionDao.getSessionsForTutorFlow(tutorId).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    suspend fun bookSession(session: TutorSession): Result<Unit> {
        val now = System.currentTimeMillis()
        val prepared = session.copy(createdAtMillis = now, updatedAtMillis = now)

        // 1. Save locally in Room
        tutorSessionDao.insertOrUpdate(
            TutorSessionEntity.fromDomainModel(prepared, syncPending = firestore == null)
        )

        // 2. Synchronize to Firestore
        if (firestore != null) {
            return try {
                firestore.collection("tutor_sessions")
                    .document(prepared.id)
                    .set(prepared.toFirestoreMap(), SetOptions.merge())
                    .await()

                tutorSessionDao.insertOrUpdate(
                    TutorSessionEntity.fromDomainModel(prepared, syncPending = false)
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Log.w("TutorSessionRepo", "Firestore sync error for booked session: ${e.message}")
                Result.failure(e)
            }
        }
        return Result.success(Unit)
    }

    suspend fun updateSessionStatus(sessionId: String, status: TutorSessionStatus): Result<Unit> {
        val now = System.currentTimeMillis()
        tutorSessionDao.updateSessionStatus(sessionId, status.name, now)

        if (firestore != null) {
            return try {
                firestore.collection("tutor_sessions")
                    .document(sessionId)
                    .set(mapOf("status" to status.name, "updatedAtMillis" to now), SetOptions.merge())
                    .await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        return Result.success(Unit)
    }

    suspend fun addFeedback(sessionId: String, rating: Double, feedback: String): Result<Unit> {
        val now = System.currentTimeMillis()
        tutorSessionDao.addSessionFeedback(sessionId, rating, feedback, now)

        if (firestore != null) {
            return try {
                firestore.collection("tutor_sessions")
                    .document(sessionId)
                    .set(
                        mapOf(
                            "studentRating" to rating,
                            "studentFeedback" to feedback,
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

    private fun attachStudentSessionsListener(studentId: String) {
        if (firestore == null) return
        activeSessionsListener?.remove()

        try {
            activeSessionsListener = firestore.collection("tutor_sessions")
                .whereEqualTo("studentId", studentId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("TutorSessionRepo", "Snapshot error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val sessions = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { TutorSession.fromFirestoreMap(doc.id, it) }
                        }
                        scope.launch {
                            tutorSessionDao.insertAll(
                                sessions.map { TutorSessionEntity.fromDomainModel(it, syncPending = false) }
                            )
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w("TutorSessionRepo", "Error attaching student sessions listener: ${e.message}")
        }
    }

    private fun attachTutorSessionsListener(tutorId: String) {
        if (firestore == null) return
        activeSessionsListener?.remove()

        try {
            activeSessionsListener = firestore.collection("tutor_sessions")
                .whereEqualTo("tutorId", tutorId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("TutorSessionRepo", "Snapshot error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val sessions = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { TutorSession.fromFirestoreMap(doc.id, it) }
                        }
                        scope.launch {
                            tutorSessionDao.insertAll(
                                sessions.map { TutorSessionEntity.fromDomainModel(it, syncPending = false) }
                            )
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w("TutorSessionRepo", "Error attaching tutor sessions listener: ${e.message}")
        }
    }

    fun cleanup() {
        activeSessionsListener?.remove()
        activeSessionsListener = null
    }
}
