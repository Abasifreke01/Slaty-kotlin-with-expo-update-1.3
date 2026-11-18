package com.example.model

/**
 * Domain Models for Student Profiles, Tutor Sessions, and Learning Tasks.
 * Serves as the clean domain representation across the UI, Room database, and Cloud Firestore.
 */

// ==========================================
// 1. STUDENT PROFILE DOMAIN MODEL
// ==========================================

data class StudentProfile(
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
    val majorSubjects: List<String> = listOf("Mathematics", "Computer Science", "Physics"),
    val studyGoals: List<String> = listOf("Complete semester courses", "Prepare for examinations"),
    val totalSessionsAttended: Int = 0,
    val totalTasksCompleted: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "userId" to userId,
        "fullName" to fullName,
        "email" to email,
        "phoneNumber" to phoneNumber,
        "institution" to institution,
        "faculty" to faculty,
        "department" to department,
        "academicLevel" to academicLevel,
        "matricNumber" to matricNumber,
        "bio" to bio,
        "avatarUrl" to avatarUrl,
        "targetGpa" to targetGpa,
        "currentGpa" to currentGpa,
        "majorSubjects" to majorSubjects,
        "studyGoals" to studyGoals,
        "totalSessionsAttended" to totalSessionsAttended,
        "totalTasksCompleted" to totalTasksCompleted,
        "createdAtMillis" to createdAtMillis,
        "updatedAtMillis" to updatedAtMillis
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromFirestoreMap(id: String, map: Map<String, Any?>): StudentProfile {
            return StudentProfile(
                id = id,
                userId = (map["userId"] as? String) ?: id,
                fullName = (map["fullName"] as? String) ?: (map["name"] as? String) ?: "Student",
                email = (map["email"] as? String) ?: "",
                phoneNumber = (map["phoneNumber"] as? String) ?: (map["phone"] as? String) ?: "",
                institution = (map["institution"] as? String) ?: "University of Lagos",
                faculty = (map["faculty"] as? String) ?: "Science",
                department = (map["department"] as? String) ?: "Computer Science",
                academicLevel = (map["academicLevel"] as? String) ?: "Undergraduate",
                matricNumber = (map["matricNumber"] as? String) ?: "",
                bio = (map["bio"] as? String) ?: "",
                avatarUrl = map["avatarUrl"] as? String,
                targetGpa = (map["targetGpa"] as? Number)?.toDouble() ?: 4.5,
                currentGpa = (map["currentGpa"] as? Number)?.toDouble() ?: 4.0,
                majorSubjects = (map["majorSubjects"] as? List<String>) ?: listOf("Mathematics", "Computer Science"),
                studyGoals = (map["studyGoals"] as? List<String>) ?: listOf("Prepare for semester tests"),
                totalSessionsAttended = (map["totalSessionsAttended"] as? Number)?.toInt() ?: 0,
                totalTasksCompleted = (map["totalTasksCompleted"] as? Number)?.toInt() ?: 0,
                createdAtMillis = (map["createdAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAtMillis = (map["updatedAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

// ==========================================
// 2. TUTOR SESSION DOMAIN MODEL & ENUMS
// ==========================================

enum class TutorSessionStatus(val displayName: String) {
    SCHEDULED("Scheduled"),
    CONFIRMED("Confirmed"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    RESCHEDULED("Rescheduled");

    companion object {
        fun fromString(value: String): TutorSessionStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SCHEDULED
        }
    }
}

enum class SessionLocationType(val displayName: String) {
    ONLINE_VIDEO("Online Video"),
    IN_PERSON("In Person"),
    HYBRID("Hybrid");

    companion object {
        fun fromString(value: String): SessionLocationType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ONLINE_VIDEO
        }
    }
}

data class TutorSession(
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
    val scheduledEndTimeMillis: Long = scheduledStartTimeMillis + (60 * 60 * 1000L),
    val durationMinutes: Int = 60,
    val fee: Double = 3500.0,
    val status: TutorSessionStatus = TutorSessionStatus.SCHEDULED,
    val locationType: SessionLocationType = SessionLocationType.ONLINE_VIDEO,
    val meetingLink: String = "",
    val roomCode: String = "slaty-${id.take(8)}",
    val sessionNotes: String = "",
    val studentRating: Double? = null,
    val studentFeedback: String? = null,
    val tutorSummary: String? = null,
    val isPaid: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to title,
        "description" to description,
        "tutorId" to tutorId,
        "tutorName" to tutorName,
        "tutorAvatar" to tutorAvatar,
        "studentId" to studentId,
        "studentName" to studentName,
        "studentAvatar" to studentAvatar,
        "subject" to subject,
        "courseCode" to courseCode,
        "scheduledStartTimeMillis" to scheduledStartTimeMillis,
        "scheduledEndTimeMillis" to scheduledEndTimeMillis,
        "durationMinutes" to durationMinutes,
        "fee" to fee,
        "status" to status.name,
        "locationType" to locationType.name,
        "meetingLink" to meetingLink,
        "roomCode" to roomCode,
        "sessionNotes" to sessionNotes,
        "studentRating" to studentRating,
        "studentFeedback" to studentFeedback,
        "tutorSummary" to tutorSummary,
        "isPaid" to isPaid,
        "createdAtMillis" to createdAtMillis,
        "updatedAtMillis" to updatedAtMillis
    )

    companion object {
        fun fromFirestoreMap(id: String, map: Map<String, Any?>): TutorSession {
            val statusStr = (map["status"] as? String) ?: TutorSessionStatus.SCHEDULED.name
            val locStr = (map["locationType"] as? String) ?: SessionLocationType.ONLINE_VIDEO.name
            val startTime = (map["scheduledStartTimeMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
            val duration = (map["durationMinutes"] as? Number)?.toInt() ?: 60

            return TutorSession(
                id = id,
                title = (map["title"] as? String) ?: "Tutoring Session",
                description = (map["description"] as? String) ?: "",
                tutorId = (map["tutorId"] as? String) ?: "",
                tutorName = (map["tutorName"] as? String) ?: "Tutor",
                tutorAvatar = map["tutorAvatar"] as? String,
                studentId = (map["studentId"] as? String) ?: "",
                studentName = (map["studentName"] as? String) ?: "Student",
                studentAvatar = map["studentAvatar"] as? String,
                subject = (map["subject"] as? String) ?: "General",
                courseCode = (map["courseCode"] as? String) ?: "",
                scheduledStartTimeMillis = startTime,
                scheduledEndTimeMillis = (map["scheduledEndTimeMillis"] as? Number)?.toLong() ?: (startTime + duration * 60 * 1000L),
                durationMinutes = duration,
                fee = (map["fee"] as? Number)?.toDouble() ?: 3500.0,
                status = TutorSessionStatus.fromString(statusStr),
                locationType = SessionLocationType.fromString(locStr),
                meetingLink = (map["meetingLink"] as? String) ?: "",
                roomCode = (map["roomCode"] as? String) ?: "slaty-${id.take(8)}",
                sessionNotes = (map["sessionNotes"] as? String) ?: "",
                studentRating = (map["studentRating"] as? Number)?.toDouble(),
                studentFeedback = map["studentFeedback"] as? String,
                tutorSummary = map["tutorSummary"] as? String,
                isPaid = (map["isPaid"] as? Boolean) ?: false,
                createdAtMillis = (map["createdAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAtMillis = (map["updatedAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

// ==========================================
// 3. LEARNING TASK DOMAIN MODEL & ENUMS
// ==========================================

enum class LearningTaskType(val displayName: String) {
    ASSIGNMENT("Assignment"),
    CBT_PRACTICE("CBT Practice"),
    TEST_PREPARATION("Test Prep"),
    EXAM_REVISION("Exam Revision"),
    PROJECT("Academic Project"),
    RESEARCH("Research Paper");

    companion object {
        fun fromString(value: String): LearningTaskType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ASSIGNMENT
        }
    }
}

enum class LearningTaskPriority(val displayName: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    URGENT("Urgent");

    companion object {
        fun fromString(value: String): LearningTaskPriority {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
        }
    }
}

enum class LearningTaskStatus(val displayName: String) {
    TODO("To Do"),
    IN_PROGRESS("In Progress"),
    REVIEW("Review"),
    COMPLETED("Completed");

    companion object {
        fun fromString(value: String): LearningTaskStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TODO
        }
    }
}

data class LearningTask(
    val id: String,
    val studentId: String,
    val studentName: String = "",
    val title: String,
    val description: String = "",
    val subject: String,
    val courseCode: String = "",
    val taskType: LearningTaskType = LearningTaskType.ASSIGNMENT,
    val priority: LearningTaskPriority = LearningTaskPriority.MEDIUM,
    val status: LearningTaskStatus = LearningTaskStatus.TODO,
    val dueDateMillis: Long = System.currentTimeMillis() + (2 * 24 * 60 * 60 * 1000L),
    val estimatedMinutes: Int = 60,
    val progressPercentage: Int = 0,
    val targetScore: Int? = null,
    val actualScore: Int? = null,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val linkedSessionId: String? = null,
    val completedAtMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val isOverdue: Boolean
        get() = status != LearningTaskStatus.COMPLETED && System.currentTimeMillis() > dueDateMillis

    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "studentId" to studentId,
        "studentName" to studentName,
        "title" to title,
        "description" to description,
        "subject" to subject,
        "courseCode" to courseCode,
        "taskType" to taskType.name,
        "priority" to priority.name,
        "status" to status.name,
        "dueDateMillis" to dueDateMillis,
        "estimatedMinutes" to estimatedMinutes,
        "progressPercentage" to progressPercentage,
        "targetScore" to targetScore,
        "actualScore" to actualScore,
        "notes" to notes,
        "tags" to tags,
        "linkedSessionId" to linkedSessionId,
        "completedAtMillis" to completedAtMillis,
        "createdAtMillis" to createdAtMillis,
        "updatedAtMillis" to updatedAtMillis
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromFirestoreMap(id: String, map: Map<String, Any?>): LearningTask {
            val typeStr = (map["taskType"] as? String) ?: LearningTaskType.ASSIGNMENT.name
            val priorityStr = (map["priority"] as? String) ?: LearningTaskPriority.MEDIUM.name
            val statusStr = (map["status"] as? String) ?: LearningTaskStatus.TODO.name

            return LearningTask(
                id = id,
                studentId = (map["studentId"] as? String) ?: "",
                studentName = (map["studentName"] as? String) ?: "",
                title = (map["title"] as? String) ?: "Learning Task",
                description = (map["description"] as? String) ?: "",
                subject = (map["subject"] as? String) ?: "General",
                courseCode = (map["courseCode"] as? String) ?: "",
                taskType = LearningTaskType.fromString(typeStr),
                priority = LearningTaskPriority.fromString(priorityStr),
                status = LearningTaskStatus.fromString(statusStr),
                dueDateMillis = (map["dueDateMillis"] as? Number)?.toLong() ?: (System.currentTimeMillis() + 86400000L),
                estimatedMinutes = (map["estimatedMinutes"] as? Number)?.toInt() ?: 60,
                progressPercentage = (map["progressPercentage"] as? Number)?.toInt() ?: 0,
                targetScore = (map["targetScore"] as? Number)?.toInt(),
                actualScore = (map["actualScore"] as? Number)?.toInt(),
                notes = (map["notes"] as? String) ?: "",
                tags = (map["tags"] as? List<String>) ?: emptyList(),
                linkedSessionId = map["linkedSessionId"] as? String,
                completedAtMillis = (map["completedAtMillis"] as? Number)?.toLong(),
                createdAtMillis = (map["createdAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAtMillis = (map["updatedAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
