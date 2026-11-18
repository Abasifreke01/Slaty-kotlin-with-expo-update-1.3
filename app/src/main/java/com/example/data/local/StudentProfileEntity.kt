package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.StudentProfile

@Entity(
    tableName = "student_profiles",
    indices = [
        Index(value = ["userId"], unique = true)
    ]
)
data class StudentProfileEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val fullName: String,
    val email: String,
    val phoneNumber: String = "",
    val institution: String = "University of Lagos",
    val faculty: String = "Science",
    val department: String = "Computer Science",
    val academicLevel: String = "Undergraduate",
    val matricNumber: String = "",
    val bio: String = "",
    val avatarUrl: String? = null,
    val targetGpa: Double = 4.5,
    val currentGpa: Double = 4.0,
    val majorSubjects: List<String> = listOf("Mathematics", "Computer Science"),
    val studyGoals: List<String> = listOf("Prepare for semester tests"),
    val totalSessionsAttended: Int = 0,
    val totalTasksCompleted: Int = 0,
    val syncPending: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toDomainModel(): StudentProfile {
        return StudentProfile(
            id = id,
            userId = userId,
            fullName = fullName,
            email = email,
            phoneNumber = phoneNumber,
            institution = institution,
            faculty = faculty,
            department = department,
            academicLevel = academicLevel,
            matricNumber = matricNumber,
            bio = bio,
            avatarUrl = avatarUrl,
            targetGpa = targetGpa,
            currentGpa = currentGpa,
            majorSubjects = majorSubjects,
            studyGoals = studyGoals,
            totalSessionsAttended = totalSessionsAttended,
            totalTasksCompleted = totalTasksCompleted,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        )
    }

    companion object {
        fun fromDomainModel(domain: StudentProfile, syncPending: Boolean = false): StudentProfileEntity {
            return StudentProfileEntity(
                id = domain.id,
                userId = domain.userId,
                fullName = domain.fullName,
                email = domain.email,
                phoneNumber = domain.phoneNumber,
                institution = domain.institution,
                faculty = domain.faculty,
                department = domain.department,
                academicLevel = domain.academicLevel,
                matricNumber = domain.matricNumber,
                bio = domain.bio,
                avatarUrl = domain.avatarUrl,
                targetGpa = domain.targetGpa,
                currentGpa = domain.currentGpa,
                majorSubjects = domain.majorSubjects,
                studyGoals = domain.studyGoals,
                totalSessionsAttended = domain.totalSessionsAttended,
                totalTasksCompleted = domain.totalTasksCompleted,
                syncPending = syncPending,
                createdAtMillis = domain.createdAtMillis,
                updatedAtMillis = domain.updatedAtMillis
            )
        }
    }
}
