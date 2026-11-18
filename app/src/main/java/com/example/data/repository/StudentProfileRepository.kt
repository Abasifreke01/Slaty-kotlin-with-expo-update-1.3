package com.example.data.repository

import android.util.Log
import com.example.data.local.StudentProfileDao
import com.example.data.local.StudentProfileEntity
import com.example.model.StudentProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class StudentProfileRepository(
    private val studentProfileDao: StudentProfileDao,
    private val firestore: FirebaseFirestore?,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var activeProfileListener: ListenerRegistration? = null

    /**
     * Observes the student profile reactively from Room database.
     * Concurrently binds a Firestore real-time snapshot listener to keep Room continuously synced.
     */
    fun observeProfile(userId: String): Flow<StudentProfile?> {
        attachFirestoreListener(userId)
        return studentProfileDao.getProfileFlow(userId).map { entity ->
            entity?.toDomainModel()
        }
    }

    suspend fun getProfile(userId: String): StudentProfile? {
        val cached = studentProfileDao.getProfileByUserId(userId)?.toDomainModel()
        if (cached != null) return cached

        // Fetch from Firestore if not yet cached locally
        if (firestore != null) {
            try {
                val doc = firestore.collection("student_profiles").document(userId).get().await()
                if (doc.exists() && doc.data != null) {
                    val profile = StudentProfile.fromFirestoreMap(doc.id, doc.data!!)
                    studentProfileDao.insertOrUpdate(StudentProfileEntity.fromDomainModel(profile, syncPending = false))
                    return profile
                }
            } catch (e: Exception) {
                Log.w("StudentProfileRepo", "Firestore fetch error: ${e.message}")
            }
        }
        return null
    }

    /**
     * Saves or updates a student profile.
     * Updates Room immediately (offline-first) and synchronizes to Cloud Firestore.
     */
    suspend fun saveProfile(profile: StudentProfile): Result<Unit> {
        val updatedProfile = profile.copy(updatedAtMillis = System.currentTimeMillis())

        // 1. Cache immediately in Room
        studentProfileDao.insertOrUpdate(
            StudentProfileEntity.fromDomainModel(updatedProfile, syncPending = firestore == null)
        )

        // 2. Synchronize to Firestore
        if (firestore != null) {
            return try {
                firestore.collection("student_profiles")
                    .document(updatedProfile.userId)
                    .set(updatedProfile.toFirestoreMap(), SetOptions.merge())
                    .await()

                // Mark sync complete locally
                studentProfileDao.insertOrUpdate(
                    StudentProfileEntity.fromDomainModel(updatedProfile, syncPending = false)
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Log.w("StudentProfileRepo", "Failed to sync profile to Firestore: ${e.message}")
                Result.failure(e)
            }
        }
        return Result.success(Unit)
    }

    suspend fun updateGpa(userId: String, currentGpa: Double, targetGpa: Double): Result<Unit> {
        val now = System.currentTimeMillis()
        studentProfileDao.updateGpa(userId, currentGpa, targetGpa, now)

        if (firestore != null) {
            return try {
                firestore.collection("student_profiles")
                    .document(userId)
                    .set(mapOf("currentGpa" to currentGpa, "targetGpa" to targetGpa, "updatedAtMillis" to now), SetOptions.merge())
                    .await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        return Result.success(Unit)
    }

    private fun attachFirestoreListener(userId: String) {
        if (firestore == null) return
        activeProfileListener?.remove()

        try {
            activeProfileListener = firestore.collection("student_profiles")
                .document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("StudentProfileRepo", "Snapshot error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists() && snapshot.data != null) {
                        val profile = StudentProfile.fromFirestoreMap(snapshot.id, snapshot.data!!)
                        scope.launch {
                            studentProfileDao.insertOrUpdate(
                                StudentProfileEntity.fromDomainModel(profile, syncPending = false)
                            )
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w("StudentProfileRepo", "Error attaching listener: ${e.message}")
        }
    }

    fun cleanup() {
        activeProfileListener?.remove()
        activeProfileListener = null
    }
}
