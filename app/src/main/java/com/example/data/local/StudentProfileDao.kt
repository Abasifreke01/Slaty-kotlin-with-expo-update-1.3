package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentProfileDao {

    @Query("SELECT * FROM student_profiles WHERE userId = :userId LIMIT 1")
    fun getProfileFlow(userId: String): Flow<StudentProfileEntity?>

    @Query("SELECT * FROM student_profiles WHERE id = :id LIMIT 1")
    fun getProfileByIdFlow(id: String): Flow<StudentProfileEntity?>

    @Query("SELECT * FROM student_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfileByUserId(userId: String): StudentProfileEntity?

    @Query("SELECT * FROM student_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): StudentProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: StudentProfileEntity)

    @Query("UPDATE student_profiles SET currentGpa = :currentGpa, targetGpa = :targetGpa, updatedAtMillis = :updatedAt WHERE userId = :userId")
    suspend fun updateGpa(userId: String, currentGpa: Double, targetGpa: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE student_profiles SET totalSessionsAttended = totalSessionsAttended + 1, updatedAtMillis = :updatedAt WHERE userId = :userId")
    suspend fun incrementSessionsAttended(userId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE student_profiles SET totalTasksCompleted = totalTasksCompleted + 1, updatedAtMillis = :updatedAt WHERE userId = :userId")
    suspend fun incrementTasksCompleted(userId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM student_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: String)

    @Query("DELETE FROM student_profiles WHERE userId = :userId")
    suspend fun deleteProfileByUserId(userId: String)

    @Query("SELECT * FROM student_profiles WHERE syncPending = 1")
    suspend fun getPendingSyncProfiles(): List<StudentProfileEntity>
}
