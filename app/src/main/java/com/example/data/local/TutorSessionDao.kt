package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TutorSessionDao {

    @Query("SELECT * FROM tutor_sessions WHERE studentId = :studentId ORDER BY scheduledStartTimeMillis DESC")
    fun getSessionsForStudentFlow(studentId: String): Flow<List<TutorSessionEntity>>

    @Query("SELECT * FROM tutor_sessions WHERE tutorId = :tutorId ORDER BY scheduledStartTimeMillis DESC")
    fun getSessionsForTutorFlow(tutorId: String): Flow<List<TutorSessionEntity>>

    @Query("SELECT * FROM tutor_sessions WHERE studentId = :studentId AND scheduledStartTimeMillis >= :currentMillis AND status NOT IN ('CANCELLED', 'COMPLETED') ORDER BY scheduledStartTimeMillis ASC")
    fun getUpcomingSessionsForStudentFlow(studentId: String, currentMillis: Long = System.currentTimeMillis()): Flow<List<TutorSessionEntity>>

    @Query("SELECT * FROM tutor_sessions WHERE id = :sessionId LIMIT 1")
    fun getSessionByIdFlow(sessionId: String): Flow<TutorSessionEntity?>

    @Query("SELECT * FROM tutor_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): TutorSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(session: TutorSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<TutorSessionEntity>)

    @Query("UPDATE tutor_sessions SET status = :status, updatedAtMillis = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionStatus(sessionId: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE tutor_sessions SET studentRating = :rating, studentFeedback = :feedback, updatedAtMillis = :updatedAt WHERE id = :sessionId")
    suspend fun addSessionFeedback(sessionId: String, rating: Double, feedback: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE tutor_sessions SET meetingLink = :link, roomCode = :roomCode, updatedAtMillis = :updatedAt WHERE id = :sessionId")
    suspend fun updateMeetingDetails(sessionId: String, link: String, roomCode: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM tutor_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("SELECT * FROM tutor_sessions WHERE syncPending = 1")
    suspend fun getPendingSyncSessions(): List<TutorSessionEntity>
}
