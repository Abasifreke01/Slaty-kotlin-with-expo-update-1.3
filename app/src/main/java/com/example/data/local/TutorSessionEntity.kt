package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.SessionLocationType
import com.example.model.TutorSession
import com.example.model.TutorSessionStatus

@Entity(
    tableName = "tutor_sessions",
    indices = [
        Index(value = ["studentId"]),
        Index(value = ["tutorId"]),
        Index(value = ["status"]),
        Index(value = ["scheduledStartTimeMillis"])
    ]
)
data class TutorSessionEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String = "",
    val tutorId: String,
    val tutorName: String,
    val tutorAvatar: String? = null,
    val studentId: String,
    val studentName: String,
    val studentAvatar: String? = null,
    val subject: String,
    val courseCode: String = "",
    val scheduledStartTimeMillis: Long,
    val scheduledEndTimeMillis: Long,
    val durationMinutes: Int = 60,
    val fee: Double = 3500.0,
    val status: String = TutorSessionStatus.SCHEDULED.name,
    val locationType: String = SessionLocationType.ONLINE_VIDEO.name,
    val meetingLink: String = "",
    val roomCode: String = "",
    val sessionNotes: String = "",
    val studentRating: Double? = null,
    val studentFeedback: String? = null,
    val tutorSummary: String? = null,
    val isPaid: Boolean = false,
    val syncPending: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toDomainModel(): TutorSession {
        return TutorSession(
            id = id,
            title = title,
            description = description,
            tutorId = tutorId,
            tutorName = tutorName,
            tutorAvatar = tutorAvatar,
            studentId = studentId,
            studentName = studentName,
            studentAvatar = studentAvatar,
            subject = subject,
            courseCode = courseCode,
            scheduledStartTimeMillis = scheduledStartTimeMillis,
            scheduledEndTimeMillis = scheduledEndTimeMillis,
            durationMinutes = durationMinutes,
            fee = fee,
            status = TutorSessionStatus.fromString(status),
            locationType = SessionLocationType.fromString(locationType),
            meetingLink = meetingLink,
            roomCode = roomCode,
            sessionNotes = sessionNotes,
            studentRating = studentRating,
            studentFeedback = studentFeedback,
            tutorSummary = tutorSummary,
            isPaid = isPaid,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        )
    }

    companion object {
        fun fromDomainModel(domain: TutorSession, syncPending: Boolean = false): TutorSessionEntity {
            return TutorSessionEntity(
                id = domain.id,
                title = domain.title,
                description = domain.description,
                tutorId = domain.tutorId,
                tutorName = domain.tutorName,
                tutorAvatar = domain.tutorAvatar,
                studentId = domain.studentId,
                studentName = domain.studentName,
                studentAvatar = domain.studentAvatar,
                subject = domain.subject,
                courseCode = domain.courseCode,
                scheduledStartTimeMillis = domain.scheduledStartTimeMillis,
                scheduledEndTimeMillis = domain.scheduledEndTimeMillis,
                durationMinutes = domain.durationMinutes,
                fee = domain.fee,
                status = domain.status.name,
                locationType = domain.locationType.name,
                meetingLink = domain.meetingLink,
                roomCode = domain.roomCode,
                sessionNotes = domain.sessionNotes,
                studentRating = domain.studentRating,
                studentFeedback = domain.studentFeedback,
                tutorSummary = domain.tutorSummary,
                isPaid = domain.isPaid,
                syncPending = syncPending,
                createdAtMillis = domain.createdAtMillis,
                updatedAtMillis = domain.updatedAtMillis
            )
        }
    }
}
