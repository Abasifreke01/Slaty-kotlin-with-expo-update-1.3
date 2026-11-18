package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SlatyRepository
import com.example.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class StudentTab(val title: String) {
    HOME("Home"),
    FIND_TUTOR("Find Tutor"),
    LIVE_CLASSES("Live Classes"),
    CONNECT("Connect"),
    PERSONAL("Personal")
}

enum class TutorTab(val title: String) {
    HOME("Home"),
    FIND_TOPICS("Find"),
    HOST_CLASS("Live"),
    CONNECT("Connect"),
    PAYOUTS("Payouts")
}

sealed interface SubScreen {
    object None : SubScreen
    data class TutorProfile(val tutor: Tutor) : SubScreen
    data class ReachTutor(val tutor: Tutor) : SubScreen
    data class CreateTask(val initialTopic: String = "") : SubScreen
    data class TaskDetails(val task: TaskItem) : SubScreen
    data class CbtPractice(val subject: String) : SubScreen
    data class CommunityChat(val community: Community) : SubScreen
    data class PrivateChat(val conversation: PrivateConversation) : SubScreen
    data class LiveVideoRoom(
        val title: String,
        val roomCode: String,
        val isHost: Boolean,
        val tutorId: String = "",
        val tutorName: String = ""
    ) : SubScreen
    object Notifications : SubScreen
    object UserProfile : SubScreen
    object AuthWelcome : SubScreen
    data class AuthLogin(val targetRole: UserRole) : SubScreen
    data class AuthRegister(val targetRole: UserRole) : SubScreen
    object ForgotPassword : SubScreen
}

data class CbtQuizState(
    val subject: String = "Biology",
    val questions: List<CbtQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val userAnswers: Map<Int, Int> = emptyMap(), // questionIndex -> selectedOptionIndex
    val secondsRemaining: Int = 900, // 15 mins default
    val isFinished: Boolean = false,
    val score: Int = 0,
    val total: Int = 0,
    val isReviewMode: Boolean = false
)

class SlatyViewModel(application: Application) : AndroidViewModel(application) {

    val repository = SlatyRepository(application.applicationContext)

    val currentUser = repository.currentUser
    val tutors = repository.tutors
    val tasks = repository.tasks
    val liveClasses = repository.liveClasses
    val communities = repository.communities
    val joinedCommunityIds = repository.joinedCommunityIds
    val privateChats = repository.privateChats
    val notifications = repository.notifications
    val filteredNotifications = repository.filteredNotifications
    val communityNotificationEvents = repository.communityNotificationEvents
    val taskRepository = repository.taskRepository
    val studentAcademicTasks = taskRepository.allTasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val studentAssignments = taskRepository.assignments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val studentTestPrepTasks = taskRepository.testPrepTasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pendingAcademicTasksCount = taskRepository.pendingCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val completedAcademicTasksCount = taskRepository.completedCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Learning Platform Models (Student Profile, Tutor Sessions, Learning Tasks via Room & Firestore)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val studentProfile: StateFlow<StudentProfile?> = currentUser.flatMapLatest { user ->
        val uid = user?.id ?: "std_demo"
        repository.studentProfileRepository.observeProfile(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val studentSessions: StateFlow<List<TutorSession>> = currentUser.flatMapLatest { user ->
        val uid = user?.id ?: "std_demo"
        repository.tutorSessionRepository.observeStudentSessions(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val upcomingSessions: StateFlow<List<TutorSession>> = currentUser.flatMapLatest { user ->
        val uid = user?.id ?: "std_demo"
        repository.tutorSessionRepository.observeUpcomingSessions(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val learningTasks: StateFlow<List<LearningTask>> = currentUser.flatMapLatest { user ->
        val uid = user?.id ?: "std_demo"
        repository.learningTaskRepository.observeTasks(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeLearningTasks: StateFlow<List<LearningTask>> = currentUser.flatMapLatest { user ->
        val uid = user?.id ?: "std_demo"
        repository.learningTaskRepository.observeActiveTasks(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateStudentGpa(currentGpa: Double, targetGpa: Double) {
        viewModelScope.launch {
            val uid = currentUser.value?.id ?: "std_demo"
            repository.studentProfileRepository.updateGpa(uid, currentGpa, targetGpa)
            showFeedback("GPA target updated to $targetGpa!")
        }
    }

    fun bookTutorSession(session: TutorSession) {
        viewModelScope.launch {
            repository.tutorSessionRepository.bookSession(session)
            showFeedback("Session booked for ${session.subject}!")
        }
    }

    fun updateSessionStatus(sessionId: String, status: TutorSessionStatus) {
        viewModelScope.launch {
            repository.tutorSessionRepository.updateSessionStatus(sessionId, status)
            showFeedback("Session marked as ${status.displayName}")
        }
    }

    fun addLearningTask(task: LearningTask) {
        viewModelScope.launch {
            val uid = currentUser.value?.id ?: "std_demo"
            val studentName = currentUser.value?.name ?: "Student"
            repository.learningTaskRepository.createTask(task.copy(studentId = uid, studentName = studentName))
            showFeedback("Learning task added!")
        }
    }

    fun updateLearningTaskProgress(taskId: String, progress: Int) {
        viewModelScope.launch {
            repository.learningTaskRepository.updateTaskProgress(taskId, progress)
        }
    }

    fun deleteLearningTask(taskId: String) {
        viewModelScope.launch {
            repository.learningTaskRepository.deleteTask(taskId)
            showFeedback("Task removed")
        }
    }

    fun toggleAcademicTask(id: String, isCompleted: Boolean) {
        viewModelScope.launch {
            taskRepository.toggleTaskCompletion(id, isCompleted)
        }
    }

    fun updateAcademicTaskProgress(id: String, progress: Int) {
        viewModelScope.launch {
            taskRepository.updateProgress(id, progress)
        }
    }

    fun createAcademicAssignment(
        title: String,
        description: String,
        subject: String,
        courseCode: String,
        dueDate: String,
        priority: com.example.data.local.StudentTaskPriority = com.example.data.local.StudentTaskPriority.MEDIUM,
        estimatedMinutes: Int = 90,
        notes: String = ""
    ) {
        viewModelScope.launch {
            val task = com.example.data.local.StudentTaskEntity.createAssignment(
                title = title,
                description = description,
                subject = subject,
                courseCode = courseCode,
                dueDate = dueDate,
                priority = priority,
                estimatedMinutes = estimatedMinutes,
                notes = notes
            )
            taskRepository.insertTask(task)
        }
    }

    fun createTestPrepTask(
        title: String,
        description: String,
        subject: String,
        courseCode: String,
        dueDate: String,
        targetScore: Int = 85,
        priority: com.example.data.local.StudentTaskPriority = com.example.data.local.StudentTaskPriority.HIGH,
        estimatedMinutes: Int = 60,
        notes: String = ""
    ) {
        viewModelScope.launch {
            val task = com.example.data.local.StudentTaskEntity.createTestPrep(
                title = title,
                description = description,
                subject = subject,
                courseCode = courseCode,
                dueDate = dueDate,
                targetScore = targetScore,
                priority = priority,
                estimatedMinutes = estimatedMinutes,
                notes = notes
            )
            taskRepository.insertTask(task)
        }
    }

    fun deleteAcademicTask(id: String) {
        viewModelScope.launch {
            taskRepository.deleteTask(id)
        }
    }

    private val _studentTab = MutableStateFlow(StudentTab.HOME)
    val studentTab: StateFlow<StudentTab> = _studentTab.asStateFlow()

    private val _tutorTab = MutableStateFlow(TutorTab.HOME)
    val tutorTab: StateFlow<TutorTab> = _tutorTab.asStateFlow()

    private val _subScreen = MutableStateFlow<SubScreen>(SubScreen.None)
    val subScreen: StateFlow<SubScreen> = _subScreen.asStateFlow()

    // Topic Action Dialog Modal
    private val _selectedTopicDialog = MutableStateFlow<String?>(null)
    val selectedTopicDialog: StateFlow<String?> = _selectedTopicDialog.asStateFlow()

    // CBT Quiz State
    private val _cbtState = MutableStateFlow(CbtQuizState())
    val cbtState: StateFlow<CbtQuizState> = _cbtState.asStateFlow()
    private var cbtTimerJob: Job? = null

    // Search & Filter state for Find Tutor
    private val _tutorSearchQuery = MutableStateFlow("")
    val tutorSearchQuery: StateFlow<String> = _tutorSearchQuery.asStateFlow()

    private val _selectedSubjectFilter = MutableStateFlow<String?>(null)
    val selectedSubjectFilter: StateFlow<String?> = _selectedSubjectFilter.asStateFlow()

    val filteredTutors: StateFlow<List<Tutor>> = combine(
        tutors,
        _tutorSearchQuery,
        _selectedSubjectFilter
    ) { allTutors, query, subject ->
        allTutors.filter { tutor ->
            val matchesQuery = query.isBlank() ||
                    tutor.name.contains(query, ignoreCase = true) ||
                    tutor.specialization.contains(query, ignoreCase = true) ||
                    tutor.subjects.any { it.contains(query, ignoreCase = true) }

            val matchesSubject = subject == null || tutor.subjects.any { it.equals(subject, ignoreCase = true) }

            matchesQuery && matchesSubject
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Home Screen Universal Search & Filtering for Tutors and Study Groups
    private val _homeSearchQuery = MutableStateFlow("")
    val homeSearchQuery: StateFlow<String> = _homeSearchQuery.asStateFlow()

    private val _homeSearchTarget = MutableStateFlow(HomeSearchTarget.ALL)
    val homeSearchTarget: StateFlow<HomeSearchTarget> = _homeSearchTarget.asStateFlow()

    private val _homeSelectedSubject = MutableStateFlow<String?>(null)
    val homeSelectedSubject: StateFlow<String?> = _homeSelectedSubject.asStateFlow()

    private val _homeSelectedLevel = MutableStateFlow<String?>(null)
    val homeSelectedLevel: StateFlow<String?> = _homeSelectedLevel.asStateFlow()

    private val _homeSortOption = MutableStateFlow(HomeSortOption.HIGHEST_RATED)
    val homeSortOption: StateFlow<HomeSortOption> = _homeSortOption.asStateFlow()

    val filteredHomeTutors: StateFlow<List<Tutor>> = combine(
        tutors,
        _homeSearchQuery,
        _homeSelectedSubject,
        _homeSelectedLevel,
        _homeSortOption
    ) { allTutors, query, subject, level, sortOption ->
        val filtered = allTutors.filter { tutor ->
            val matchesQuery = query.isBlank() ||
                    tutor.name.contains(query, ignoreCase = true) ||
                    tutor.specialization.contains(query, ignoreCase = true) ||
                    tutor.bio.contains(query, ignoreCase = true) ||
                    tutor.subjects.any { it.contains(query, ignoreCase = true) }

            val matchesSubject = subject.isNullOrBlank() || subject.equals("All Subjects", ignoreCase = true) ||
                    tutor.subjects.any { it.equals(subject, ignoreCase = true) || it.contains(subject, ignoreCase = true) }

            val matchesLevel = level.isNullOrBlank() || level.equals("All Levels", ignoreCase = true) ||
                    tutor.academicLevels.any { it.equals(level, ignoreCase = true) }

            matchesQuery && matchesSubject && matchesLevel
        }

        when (sortOption) {
            HomeSortOption.HIGHEST_RATED -> filtered.sortedWith(
                compareByDescending<Tutor> { it.rating }.thenByDescending { it.reviewsCount }
            )
            HomeSortOption.PRICE_LOW_TO_HIGH -> filtered.sortedWith(
                compareBy<Tutor> { it.hourlyRate }.thenByDescending { it.rating }
            )
            HomeSortOption.NEWEST -> filtered.reversed()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredHomeCommunities: StateFlow<List<Community>> = combine(
        communities,
        _homeSearchQuery,
        _homeSelectedSubject,
        _homeSelectedLevel,
        _homeSortOption
    ) { allComms, query, subject, level, sortOption ->
        val filtered = allComms.filter { comm ->
            val matchesQuery = query.isBlank() ||
                    comm.name.contains(query, ignoreCase = true) ||
                    comm.description.contains(query, ignoreCase = true) ||
                    comm.category.contains(query, ignoreCase = true) ||
                    comm.subjects.any { it.contains(query, ignoreCase = true) }

            val matchesSubject = subject.isNullOrBlank() || subject.equals("All Subjects", ignoreCase = true) ||
                    comm.subjects.any { it.equals(subject, ignoreCase = true) || it.contains(subject, ignoreCase = true) } ||
                    comm.category.contains(subject, ignoreCase = true)

            val matchesLevel = level.isNullOrBlank() || level.equals("All Levels", ignoreCase = true) ||
                    comm.academicLevels.any { it.equals(level, ignoreCase = true) }

            matchesQuery && matchesSubject && matchesLevel
        }

        when (sortOption) {
            HomeSortOption.HIGHEST_RATED -> filtered.sortedByDescending { it.memberCount }
            HomeSortOption.PRICE_LOW_TO_HIGH -> filtered.sortedBy { it.name }
            HomeSortOption.NEWEST -> filtered.reversed()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setHomeSearchQuery(query: String) {
        _homeSearchQuery.value = query
    }

    fun setHomeSearchTarget(target: HomeSearchTarget) {
        _homeSearchTarget.value = target
    }

    fun setHomeSubjectFilter(subject: String?) {
        _homeSelectedSubject.value = if (subject == "All Subjects") null else subject
    }

    fun setHomeLevelFilter(level: String?) {
        _homeSelectedLevel.value = if (level == "All Levels") null else level
    }

    fun setHomeSortOption(sortOption: HomeSortOption) {
        _homeSortOption.value = sortOption
    }

    fun clearHomeFilters() {
        _homeSearchQuery.value = ""
        _homeSearchTarget.value = HomeSearchTarget.ALL
        _homeSelectedSubject.value = null
        _homeSelectedLevel.value = null
        _homeSortOption.value = HomeSortOption.HIGHEST_RATED
    }

    // Snackbars / Feedback
    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    fun showFeedback(message: String) {
        _feedbackMessage.value = message
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun setStudentTab(tab: StudentTab) {
        _studentTab.value = tab
        _subScreen.value = SubScreen.None
    }

    fun setTutorTab(tab: TutorTab) {
        _tutorTab.value = tab
        _subScreen.value = SubScreen.None
    }

    fun navigateTo(sub: SubScreen) {
        _subScreen.value = sub
        val activeCommId = (sub as? SubScreen.CommunityChat)?.community?.id
        repository.setActiveCommunityChat(activeCommId)
    }

    fun goBack(): Boolean {
        if (_subScreen.value !is SubScreen.None) {
            _subScreen.value = SubScreen.None
            repository.setActiveCommunityChat(null)
            return true
        }
        return false
    }

    fun openTopicModal(topic: String) {
        _selectedTopicDialog.value = topic
    }

    fun closeTopicModal() {
        _selectedTopicDialog.value = null
    }

    fun setTutorSearch(q: String) {
        _tutorSearchQuery.value = q
    }

    fun setSubjectFilter(subj: String?) {
        _selectedSubjectFilter.value = if (_selectedSubjectFilter.value == subj) null else subj
    }

    // Role Switch
    fun switchRole(newRole: UserRole) {
        repository.switchUserRole(newRole)
        _subScreen.value = SubScreen.None
        if (newRole == UserRole.STUDENT) {
            _studentTab.value = StudentTab.HOME
        } else {
            _tutorTab.value = TutorTab.HOME
        }
        showFeedback("Switched to ${newRole.displayName} mode")
    }

    fun logout() {
        repository.logout()
        _subScreen.value = SubScreen.AuthWelcome
    }

    fun loginUser(email: String, role: UserRole) {
        repository.login(email, role)
        _subScreen.value = SubScreen.None
        if (role == UserRole.STUDENT) {
            _studentTab.value = StudentTab.HOME
        } else {
            _tutorTab.value = TutorTab.HOME
        }
        showFeedback("Welcome to Slaty, ${if (role == UserRole.TUTOR) "Tutor" else "Student"}!")
    }

    fun loginWithFirebaseUser(user: User) {
        repository.setUser(user)
        _subScreen.value = SubScreen.None
        if (user.role == UserRole.STUDENT) {
            _studentTab.value = StudentTab.HOME
        } else {
            _tutorTab.value = TutorTab.HOME
        }
        val greeting = if (user.role == UserRole.TUTOR) "Dr. " + user.name.removePrefix("Dr. ") else user.name
        showFeedback("Welcome to Slaty, $greeting!")
    }

    fun updateUser(user: User) {
        repository.setUser(user)
        showFeedback("Profile changes updated!")
    }

    // CBT Mock Subjects from Firebase Database
    val cbtSubjects: StateFlow<List<CbtSubject>> = repository.cbtSubjects

    // CBT Quiz Flow
    fun startCbtQuiz(subject: String) {
        viewModelScope.launch {
            val qList = repository.fetchQuestionsFromFirebase(subject)
            _cbtState.value = CbtQuizState(
                subject = subject,
                questions = qList,
                currentIndex = 0,
                userAnswers = emptyMap(),
                secondsRemaining = 900,
                isFinished = false,
                score = 0,
                total = qList.size,
                isReviewMode = false
            )
            _subScreen.value = SubScreen.CbtPractice(subject)

            cbtTimerJob?.cancel()
            cbtTimerJob = viewModelScope.launch {
                while (_cbtState.value.secondsRemaining > 0 && !_cbtState.value.isFinished) {
                    delay(1000)
                    _cbtState.value = _cbtState.value.copy(
                        secondsRemaining = _cbtState.value.secondsRemaining - 1
                    )
                }
                if (_cbtState.value.secondsRemaining <= 0 && !_cbtState.value.isFinished) {
                    submitCbtQuiz()
                }
            }
        }
    }

    fun selectCbtAnswer(questionIndex: Int, optionIndex: Int) {
        val answers = _cbtState.value.userAnswers.toMutableMap()
        answers[questionIndex] = optionIndex
        _cbtState.value = _cbtState.value.copy(userAnswers = answers)
    }

    fun setCbtQuestionIndex(idx: Int) {
        if (idx in 0 until _cbtState.value.questions.size) {
            _cbtState.value = _cbtState.value.copy(currentIndex = idx)
        }
    }

    fun submitCbtQuiz() {
        cbtTimerJob?.cancel()
        val state = _cbtState.value
        var correct = 0
        state.questions.forEachIndexed { i, q ->
            val userChoice = state.userAnswers[i]
            if (userChoice != null && userChoice == q.correctIndex) {
                correct++
            }
        }
        _cbtState.value = state.copy(
            isFinished = true,
            score = correct,
            total = state.questions.size,
            isReviewMode = true
        )
        val user = currentUser.value
        if (user != null) {
            viewModelScope.launch {
                repository.saveCbtResult(user.id, state.subject, correct, state.questions.size)
            }
        }
    }

    fun restartCbtQuiz() {
        startCbtQuiz(_cbtState.value.subject)
    }

    // Task Submission
    fun submitTask(
        title: String,
        courseCode: String,
        institution: String,
        faculty: String,
        department: String,
        description: String,
        amount: Double,
        deadline: String,
        learnerType: String
    ) {
        val created = repository.createTask(
            title = title,
            courseCode = courseCode,
            institution = institution,
            faculty = faculty,
            department = department,
            description = description,
            amount = amount,
            deadline = deadline,
            learnerType = learnerType
        )
        showFeedback("Task #${created.id.takeLast(4)} submitted successfully!")
        _subScreen.value = SubScreen.None
    }

    // Tutor Accepts Task
    fun acceptTaskByTutor(task: TaskItem) {
        val me = currentUser.value
        val tutorName = me?.name ?: "Dr. Emmanuel Okon"
        val tutorId = me?.id ?: "tutor_current"
        val success = repository.acceptTask(task.id, tutorName, tutorId)
        if (success) {
            showFeedback("Task accepted! Moved to your active dashboard.")
            _subScreen.value = SubScreen.None
        } else {
            showFeedback("Task is already accepted or unavailable.")
        }
    }

    // Tutor Completes Task
    fun completeTaskByTutor(taskId: String) {
        val success = repository.completeTask(taskId)
        if (success) {
            showFeedback("Task marked as completed! Payment credited to your earnings.")
            _subScreen.value = SubScreen.None
        }
    }

    // Host Class
    fun hostNewLiveClass(
        title: String,
        fee: Double,
        subject: String = "General",
        scheduledDate: String = "Today",
        scheduledTime: String = "Starting Now",
        durationMinutes: Int = 60
    ) {
        val session = repository.createLiveClass(
            title = title,
            fee = fee,
            subject = subject,
            scheduledDate = scheduledDate,
            scheduledTime = scheduledTime,
            durationMinutes = durationMinutes
        )
        val feedback = if (fee == 0.0) "Free class scheduled: ${session.roomCode}" else "Live class created: ${session.roomCode}"
        showFeedback(feedback)
        _subScreen.value = SubScreen.LiveVideoRoom(
            title = session.title,
            roomCode = session.roomCode,
            isHost = true,
            tutorId = session.tutorId,
            tutorName = session.tutorName
        )
    }

    fun submitTutorRating(
        tutorId: String,
        tutorName: String,
        roomCode: String,
        stars: Int,
        comment: String
    ) {
        repository.submitTutorRating(tutorId, tutorName, roomCode, stars, comment)
        showFeedback("Thanks! Your $stars-star rating for $tutorName has been saved.")
    }

    fun joinCommunity(id: String) {
        repository.joinCommunity(id)
        showFeedback("Joined study group! You can now view its notifications and chat.")
    }

    fun leaveCommunity(id: String) {
        repository.leaveCommunity(id)
        showFeedback("Left study group.")
    }

    fun isCommunityJoined(id: String): Boolean {
        return repository.isCommunityJoined(id)
    }

    fun deleteCommunity(id: String) {
        val deleted = repository.deleteCommunity(id)
        if (deleted) {
            showFeedback("Study community deleted for all members.")
        } else {
            showFeedback("Only the group creator can delete this community.")
        }
    }

    // Payout details update
    fun updatePayoutMethod(bank: String, accountNum: String, accountName: String) {
        repository.updatePayoutDetails(bank, accountNum, accountName)
        showFeedback("Payout bank account updated successfully!")
    }

    // Profile update
    fun updateUserProfile(name: String, email: String, phone: String, spec: String? = null) {
        repository.updateProfile(name, email, phone, spec)
        showFeedback("Profile changes saved!")
    }

    // Community & Private Chat Operations
    fun observeCommunityMessages(communityId: String): Flow<List<ChatMessage>> {
        return repository.observeCommunityMessages(communityId)
    }

    fun observePrivateChatMessages(chatId: String, otherUserName: String, initialLastMessage: String): Flow<List<ChatMessage>> {
        return repository.observePrivateChatMessages(chatId, otherUserName, initialLastMessage)
    }

    fun sendCommunityMsg(communityId: String, text: String) {
        if (text.isNotBlank()) {
            repository.sendCommunityMessage(communityId, text)
        }
    }

    fun sendPrivateMsg(chatId: String, text: String, otherUserId: String, otherUserName: String, otherUserRole: UserRole) {
        if (text.isNotBlank()) {
            repository.sendPrivateMessage(chatId, text, otherUserId, otherUserName, otherUserRole)
        }
    }

    fun createNewCommunity(name: String, desc: String, category: String) {
        repository.createCommunity(name, desc, category)
        showFeedback("Study community '$name' created!")
    }
}
