package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.LearningTask
import com.example.model.LearningTaskPriority
import com.example.model.LearningTaskStatus
import com.example.model.LearningTaskType

@Entity(
    tableName = "learning_tasks",
    indices = [
        Index(value = ["studentId"]),
        Index(value = ["status"]),
        Index(value = ["dueDateMillis"]),
        Index(value = ["priority"])
    ]
)
data class LearningTaskEntity(
    @PrimaryKey
    val id: String,
    val studentId: String,
    val studentName: String = "",
    val title: String,
    val description: String = "",
    val subject: String,
    val courseCode: String = "",
    val taskType: String = LearningTaskType.ASSIGNMENT.name,
    val priority: String = LearningTaskPriority.MEDIUM.name,
    val status: String = LearningTaskStatus.TODO.name,
    val dueDateMillis: Long,
    val estimatedMinutes: Int = 60,
    val progressPercentage: Int = 0,
    val targetScore: Int? = null,
    val actualScore: Int? = null,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val linkedSessionId: String? = null,
    val completedAtMillis: Long? = null,
    val syncPending: Boolean = false,
    val isDeletedLocally: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toDomainModel(): LearningTask {
        return LearningTask(
            id = id,
            studentId = studentId,
            studentName = studentName,
            title = title,
            description = description,
            subject = subject,
            courseCode = courseCode,
            taskType = LearningTaskType.fromString(taskType),
            priority = LearningTaskPriority.fromString(priority),
            status = LearningTaskStatus.fromString(status),
            dueDateMillis = dueDateMillis,
            estimatedMinutes = estimatedMinutes,
            progressPercentage = progressPercentage,
            targetScore = targetScore,
            actualScore = actualScore,
            notes = notes,
            tags = tags,
            linkedSessionId = linkedSessionId,
            completedAtMillis = completedAtMillis,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        )
    }

    companion object {
        fun fromDomainModel(domain: LearningTask, syncPending: Boolean = false): LearningTaskEntity {
            return LearningTaskEntity(
                id = domain.id,
                studentId = domain.studentId,
                studentName = domain.studentName,
                title = domain.title,
                description = domain.description,
                subject = domain.subject,
                courseCode = domain.courseCode,
                taskType = domain.taskType.name,
                priority = domain.priority.name,
                status = domain.status.name,
                dueDateMillis = domain.dueDateMillis,
                estimatedMinutes = domain.estimatedMinutes,
                progressPercentage = domain.progressPercentage,
                targetScore = domain.targetScore,
                actualScore = domain.actualScore,
                notes = domain.notes,
                tags = domain.tags,
                linkedSessionId = domain.linkedSessionId,
                completedAtMillis = domain.completedAtMillis,
                syncPending = syncPending,
                isDeletedLocally = false,
                createdAtMillis = domain.createdAtMillis,
                updatedAtMillis = domain.updatedAtMillis
            )
        }
    }
}
