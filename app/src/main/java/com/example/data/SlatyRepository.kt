package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.repository.TaskSyncRepository
import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SlatyRepository(private val context: Context) {

    companion object {
        fun calculateNewAverageRating(
            currentRating: Double,
            currentCount: Int,
            newStars: Int
        ): Pair<Double, Int> {
            val clampedStars = newStars.coerceIn(1, 5).toDouble()
            val safeCount = maxOf(0, currentCount)
            val newCount = safeCount + 1
            val rawAvg = ((currentRating * safeCount) + clampedStars) / newCount
            val rounded = Math.round(rawAvg * 100.0) / 100.0
            return rounded to newCount
        }
    }

    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val taskSyncRepository = TaskSyncRepository.create(context, repoScope)
    val liveClassRepository by lazy {
        com.example.data.repository.LiveClassRepository(firestore)
    }
    val taskRepository = com.example.data.repository.TaskRepository(context).apply {
        repoScope.launch {
            seedInitialAcademicTasksIfEmpty()
        }
    }

    private val localDatabase by lazy { com.example.data.local.SlatyDatabase.getInstance(context) }

    val studentProfileRepository by lazy {
        com.example.data.repository.StudentProfileRepository(
            studentProfileDao = localDatabase.studentProfileDao(),
            firestore = firestore,
            scope = repoScope
        )
    }

    val tutorSessionRepository by lazy {
        com.example.data.repository.TutorSessionRepository(
            tutorSessionDao = localDatabase.tutorSessionDao(),
            firestore = firestore,
            scope = repoScope
        )
    }

    val learningTaskRepository by lazy {
        com.example.data.repository.LearningTaskRepository(
            learningTaskDao = localDatabase.learningTaskDao(),
            firestore = firestore,
            scope = repoScope
        )
    }

    // Lazy Firestore Instance initialized with R.string.firestore_database_id
    val firestore: com.google.firebase.firestore.FirebaseFirestore? by lazy {
        try {
            val dbId = try { context.getString(com.example.R.string.firestore_database_id).trim() } catch (e: Exception) { "" }
            if (dbId.isNotBlank() && dbId != "(default)") {
                try {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance(dbId)
                } catch (e: Exception) {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                }
            } else {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
            }
        } catch (e: Exception) {
            try {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
            } catch (ex: Exception) {
                null
            }
        }
    }

    // List of CBT Mock Subjects from Firebase Database
    private val _cbtSubjects = MutableStateFlow<List<CbtSubject>>(
        listOf(
            CbtSubject("biology", "Biology", "BIO-JAMB", "Cell biology, genetics, ecology & physiology", 40, "biotech"),
            CbtSubject("mathematics", "Mathematics", "MTH-UTME", "Calculus, trigonometry, algebra & vectors", 40, "calculate"),
            CbtSubject("physics", "Physics", "PHY-JAMB", "Mechanics, waves, electricity & optics", 40, "bolt"),
            CbtSubject("chemistry", "Chemistry", "CHM-JAMB", "Organic, physical, stoichiometry & periodicity", 40, "science"),
            CbtSubject("english", "English Language", "ENG-UTME", "Comprehension, lexis, structure & idioms", 40, "menu_book"),
            CbtSubject("economics", "Economics", "ECN-JAMB", "Market structures, inflation & national income", 40, "trending_up"),
            CbtSubject("government", "Government", "GOV-JAMB", "Political systems, constitution & institutions", 40, "account_balance")
        )
    )
    val cbtSubjects: StateFlow<List<CbtSubject>> = _cbtSubjects.asStateFlow()

    // Current User Session (backed by Firebase Authentication)
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        // Sync CBT Subjects and Question Banks with Firebase Database
        syncCbtSubjectsWithFirebase()

        try {
            val fUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (fUser != null) {
                repoScope.launch {
                    try {
                        val uid = fUser.uid
                        val email = fUser.email ?: ""
                        val displayName = fUser.displayName ?: email.substringBefore("@")
                        val fs = firestore
                        if (fs != null) {
                            val doc = fs.collection("users").document(uid).get().await()
                            if (doc.exists()) {
                                val roleStr = (doc.getString("role") ?: "student").lowercase()
                                val role = if (roleStr == "tutor") UserRole.TUTOR else UserRole.STUDENT
                                _currentUser.value = User(
                                    id = uid,
                                    name = doc.getString("name") ?: displayName,
                                    username = doc.getString("username") ?: displayName.lowercase().replace(" ", "_"),
                                    email = doc.getString("email") ?: email,
                                    phone = doc.getString("phone") ?: "",
                                    role = role,
                                    specialization = doc.getString("specialization"),
                                    bio = doc.getString("bio") ?: "",
                                    hourlyRate = doc.getDouble("hourlyRate") ?: 3500.0,
                                    rating = doc.getDouble("rating") ?: 4.9,
                                    reviewsCount = doc.getLong("reviewsCount")?.toInt() ?: 0,
                                    studentsCount = doc.getLong("studentsCount")?.toInt() ?: 142,
                                    bankName = doc.getString("bankName") ?: "GTBank",
                                    accountNumber = doc.getString("accountNumber") ?: "0123456789",
                                    accountName = doc.getString("accountName") ?: displayName,
                                    institution = doc.getString("institution") ?: "University of Lagos",
                                    faculty = doc.getString("faculty") ?: "Science",
                                    department = doc.getString("department") ?: "Computer Science",
                                    academicLevel = doc.getString("academicLevel") ?: "Undergraduate"
                                )
                                updateOnlinePresence(uid, true)
                            } else {
                                _currentUser.value = User(
                                    id = uid,
                                    name = displayName,
                                    username = displayName.lowercase().replace(" ", "_"),
                                    email = email,
                                    role = UserRole.STUDENT
                                )
                                updateOnlinePresence(uid, true)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("SlatyRepo", "Could not fetch remote profile on boot: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SlatyRepo", "Firebase not yet initialized on repo creation: ${e.message}")
        }

        // Real-time synchronization of live video classes across devices
        repoScope.launch {
            try {
                liveClassRepository.observeLiveClasses().collect { remoteList ->
                    if (remoteList.isNotEmpty()) {
                        val currentList = _liveClasses.value
                        val merged = (remoteList + currentList).distinctBy { it.roomCode }
                        _liveClasses.value = merged
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("SlatyRepo", "Live classes sync error: ${e.message}")
            }
        }

        // Real-time cross-device sync for Study Communities, online notifications, and Tutor ratings
        observeCommunitiesFromFirestore()
        observeTutorsFromFirestore()
    }

    // Tutors List
    private val _tutors = MutableStateFlow<List<Tutor>>(
        listOf(
            Tutor(
                id = "tutor_1",
                name = "Dr. Emmanuel Okon",
                username = "emmanuel_okon",
                specialization = "Senior Lecturer, Physics & Engineering",
                rating = 4.95,
                reviewsCount = 128,
                hourlyRate = 4500.0,
                bio = "Ph.D. in Applied Physics with 8+ years preparing university students for exams and complex mechanics problems.",
                subjects = listOf("Physics", "Thermodynamics", "Electromagnetism", "Mathematics"),
                avatarResName = "avatar_3d_12",
                academicLevels = listOf("Undergraduate", "Postgraduate", "JAMB/UTME")
            ),
            Tutor(
                id = "tutor_2",
                name = "Amina Bello, M.Sc.",
                username = "amina_chem",
                specialization = "Organic Chemistry Specialist",
                rating = 4.88,
                reviewsCount = 94,
                hourlyRate = 3800.0,
                bio = "Specialized in General Chemistry, Organic synthesis, and JAMB/WAEC advanced preparation.",
                subjects = listOf("Chemistry", "Organic Chemistry", "Biology"),
                avatarResName = "avatar_3d_16",
                academicLevels = listOf("Secondary (WAEC/NECO)", "JAMB/UTME", "Undergraduate")
            ),
            Tutor(
                id = "tutor_3",
                name = "Chukwudi Nnamdi",
                username = "chuks_maths",
                specialization = "Pure & Applied Mathematics",
                rating = 4.92,
                reviewsCount = 210,
                hourlyRate = 4000.0,
                bio = "First class mathematics graduate with a passion for simplifying Differential Equations, Linear Algebra, and Calculus.",
                subjects = listOf("Mathematics", "Calculus", "Statistics"),
                avatarResName = "avatar_3d_19",
                academicLevels = listOf("JAMB/UTME", "Undergraduate")
            ),
            Tutor(
                id = "tutor_4",
                name = "Grace Eyo",
                username = "grace_biology",
                specialization = "Cellular Biology & Genetics",
                rating = 4.85,
                reviewsCount = 76,
                hourlyRate = 3500.0,
                bio = "Passionate tutor dedicated to helping pre-med and science students ace their courses with clear visual analogies.",
                subjects = listOf("Biology", "Genetics", "Physiology"),
                avatarResName = "avatar_3d_21",
                academicLevels = listOf("Secondary (WAEC/NECO)", "JAMB/UTME", "Undergraduate")
            ),
            Tutor(
                id = "tutor_5",
                name = "Babatunde Alabi",
                username = "tunde_econ",
                specialization = "Economics & Financial Mathematics",
                rating = 4.90,
                reviewsCount = 115,
                hourlyRate = 4200.0,
                bio = "Economist with extensive coaching background in Macroeconomics, Microeconomics, and Econometrics.",
                subjects = listOf("Economics", "Microeconomics", "Business Math"),
                avatarResName = "avatar_monogram",
                academicLevels = listOf("Undergraduate", "Postgraduate")
            )
        )
    )
    val tutors: StateFlow<List<Tutor>> = _tutors.asStateFlow()

    // Tasks List backed by Room-to-Firestore TaskSyncRepository
    val tasks: StateFlow<List<TaskItem>> = taskSyncRepository.allTasks.stateIn(
        scope = repoScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    // Live Classes
    private val _liveClasses = MutableStateFlow<List<LiveClassSession>>(
        listOf(
            LiveClassSession(
                id = "class_1",
                title = "Mastering Differential Equations for Engineering",
                tutorName = "Dr. Emmanuel Okon",
                tutorId = "tutor_1",
                subject = "Mathematics",
                scheduledTime = "Today, 7:00 PM",
                fee = 3500.0,
                attendeesCount = 18,
                roomCode = "slaty-diff-eq-live",
                isLive = true
            ),
            LiveClassSession(
                id = "class_2",
                title = "JAMB Chemistry High-Yield Exam Masterclass",
                tutorName = "Amina Bello, M.Sc.",
                tutorId = "tutor_2",
                subject = "Chemistry",
                scheduledTime = "Tomorrow, 4:00 PM",
                fee = 2000.0,
                attendeesCount = 34,
                roomCode = "slaty-jamb-chem-class",
                isLive = false
            ),
            LiveClassSession(
                id = "class_3",
                title = "Biology Cell Structure & Human Physiology",
                tutorName = "Grace Eyo",
                tutorId = "tutor_4",
                subject = "Biology",
                scheduledTime = "Saturday, 11:00 AM",
                fee = 2500.0,
                attendeesCount = 22,
                roomCode = "slaty-bio-physiology",
                isLive = false
            )
        )
    )
    val liveClasses: StateFlow<List<LiveClassSession>> = _liveClasses.asStateFlow()

    // Communities
    private val _communities = MutableStateFlow<List<Community>>(
        listOf(
            Community(
                id = "comm_1",
                name = "JAMB & UTME Master Prep",
                description = "Discussions, past questions analysis, and daily tips for top scores in Nigeria.",
                memberCount = 1420,
                category = "Exam Prep",
                unreadCount = 4,
                subjects = listOf("Mathematics", "Physics", "Chemistry", "Biology"),
                academicLevels = listOf("JAMB/UTME")
            ),
            Community(
                id = "comm_2",
                name = "Engineering & Physical Sciences",
                description = "Mechanics, Circuits, Differential Equations, and MATLAB study groups for undergraduates.",
                memberCount = 890,
                category = "Higher Ed",
                unreadCount = 2,
                subjects = listOf("Engineering", "Physics", "Mathematics"),
                academicLevels = listOf("Undergraduate", "Postgraduate")
            ),
            Community(
                id = "comm_3",
                name = "Pre-Med & Biological Sciences",
                description = "Anatomy, Biochemistry, Physiology, and MBBS entry discussions.",
                memberCount = 720,
                category = "Medical",
                unreadCount = 0,
                subjects = listOf("Biology", "Chemistry"),
                academicLevels = listOf("JAMB/UTME", "Undergraduate")
            ),
            Community(
                id = "comm_4",
                name = "WAEC & NECO Senior Scholars",
                description = "Targeted practice for Senior Secondary examinations and syllabus coverage.",
                memberCount = 1150,
                category = "Secondary",
                unreadCount = 7,
                subjects = listOf("Mathematics", "Physics", "Chemistry", "Biology", "Economics"),
                academicLevels = listOf("Secondary (WAEC/NECO)")
            ),
            Community(
                id = "comm_5",
                name = "General Student Lounge",
                description = "Connect, share study tips, ask peer questions, and find study buddies.",
                memberCount = 2300,
                category = "Social",
                unreadCount = 1,
                subjects = listOf("General", "Economics", "Mathematics"),
                academicLevels = listOf("Secondary (WAEC/NECO)", "JAMB/UTME", "Undergraduate", "Postgraduate")
            )
        )
    )
    val communities: StateFlow<List<Community>> = _communities.asStateFlow()

    // Private Chats
    private val _privateChats = MutableStateFlow<List<PrivateConversation>>(
        listOf(
            PrivateConversation(
                id = "chat_tutor_1",
                otherUserId = "tutor_1",
                otherUserName = "Dr. Emmanuel Okon",
                otherUserRole = UserRole.TUTOR,
                lastMessage = "I've uploaded the worksheet for our thermodynamics class.",
                lastMessageTime = "10:14 AM",
                unreadCount = 1,
                avatarRes = "avatar_3d_12"
            ),
            PrivateConversation(
                id = "chat_tutor_2",
                otherUserId = "tutor_2",
                otherUserName = "Amina Bello, M.Sc.",
                otherUserRole = UserRole.TUTOR,
                lastMessage = "Let me know when you review the organic chemistry notes.",
                lastMessageTime = "Yesterday",
                unreadCount = 0,
                avatarRes = "avatar_3d_16"
            ),
            PrivateConversation(
                id = "chat_tutor_3",
                otherUserId = "tutor_3",
                otherUserName = "Chukwudi Nnamdi",
                otherUserRole = UserRole.TUTOR,
                lastMessage = "Great job on the vector calculus drill!",
                lastMessageTime = "2 days ago",
                unreadCount = 0,
                avatarRes = "avatar_3d_19"
            )
        )
    )
    val privateChats: StateFlow<List<PrivateConversation>> = _privateChats.asStateFlow()

    // Community Messages map (communityId -> list of messages)
    private val _communityMessages = MutableStateFlow<Map<String, List<ChatMessage>>>(
        mapOf(
            "comm_1" to listOf(
                ChatMessage("m1", "usr_1", "Tunde Balogun", UserRole.STUDENT, "Has anyone solved question 14 on the 2024 Physics paper?"),
                ChatMessage("m2", "tutor_1", "Dr. Emmanuel Okon", UserRole.TUTOR, "Remember to use conservation of linear momentum before considering thermal dissipation."),
                ChatMessage("m3", "usr_2", "Adaeze Chukwu", UserRole.STUDENT, "Thank you Dr. Okon! That cleared up the discrepancy in my formula.")
            ),
            "comm_2" to listOf(
                ChatMessage("m4", "usr_3", "Ibrahim Sani", UserRole.STUDENT, "Anyone working on Laplace transform problems for circuit analysis?"),
                ChatMessage("m5", "tutor_3", "Chukwudi Nnamdi", UserRole.TUTOR, "I can host a quick 20-minute live session later this evening if interested!")
            )
        )
    )

    // Joined Communities tracker (Persisted in SharedPreferences)
    private val joinedPrefs by lazy {
        context.getSharedPreferences("slaty_joined_groups_prefs", Context.MODE_PRIVATE)
    }

    private val _joinedCommunityIds = MutableStateFlow<Set<String>>(
        joinedPrefs.getStringSet("joined_ids", setOf("comm_1")) ?: setOf("comm_1")
    )
    val joinedCommunityIds: StateFlow<Set<String>> = _joinedCommunityIds.asStateFlow()

    private val _communityNotificationEvents = MutableSharedFlow<CommunityNotificationEvent>(extraBufferCapacity = 16)
    val communityNotificationEvents: SharedFlow<CommunityNotificationEvent> = _communityNotificationEvents.asSharedFlow()

    private val _activeCommunityChatId = MutableStateFlow<String?>(null)
    private val lastNotifiedCommunityMsgTime = ConcurrentHashMap<String, Long>()
    private var initialCommunitySnapshotLoaded = false

    fun setActiveCommunityChat(communityId: String?) {
        _activeCommunityChatId.value = communityId
        if (communityId != null) {
            _communities.value = _communities.value.map {
                if (it.id == communityId) it.copy(unreadCount = 0) else it
            }
        }
    }

    private fun getCurrentActorId(): String {
        val localId = _currentUser.value?.id
        if (!localId.isNullOrBlank()) return localId
        return try { FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }
    }

    private fun isCurrentActor(senderId: String): Boolean {
        if (senderId.isBlank()) return false
        val localId = _currentUser.value?.id
        val authUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
        return (localId != null && senderId == localId) || (authUid != null && senderId == authUid)
    }

    private fun updateOnlinePresence(uid: String, isOnline: Boolean) {
        val fs = firestore ?: return
        if (uid.isBlank()) return
        repoScope.launch {
            try {
                fs.collection("users").document(uid).set(
                    mapOf(
                        "isOnline" to isOnline,
                        "lastSeenAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()
            } catch (_: Exception) {
            }
        }
    }

    fun joinCommunity(communityId: String) {
        val updated = _joinedCommunityIds.value + communityId
        _joinedCommunityIds.value = updated
        joinedPrefs.edit().putStringSet("joined_ids", updated).apply()
        val comm = _communities.value.firstOrNull { it.id == communityId }
        if (comm != null) {
            _communities.value = _communities.value.map {
                if (it.id == communityId) it.copy(memberCount = it.memberCount + 1) else it
            }
        }
        val fs = firestore
        if (fs != null) {
            repoScope.launch {
                try {
                    fs.collection("communities").document(communityId)
                        .set(mapOf("memberCount" to FieldValue.increment(1)), SetOptions.merge())
                        .await()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun leaveCommunity(communityId: String) {
        val updated = _joinedCommunityIds.value - communityId
        _joinedCommunityIds.value = updated
        joinedPrefs.edit().putStringSet("joined_ids", updated).apply()
        val comm = _communities.value.firstOrNull { it.id == communityId }
        if (comm != null) {
            _communities.value = _communities.value.map {
                if (it.id == communityId) it.copy(memberCount = maxOf(1, it.memberCount - 1)) else it
            }
        }
        val fs = firestore
        if (fs != null && (comm?.memberCount ?: 1) > 1) {
            repoScope.launch {
                try {
                    fs.collection("communities").document(communityId)
                        .set(mapOf("memberCount" to FieldValue.increment(-1)), SetOptions.merge())
                        .await()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun isCommunityJoined(communityId: String): Boolean {
        return _joinedCommunityIds.value.contains(communityId)
    }

    fun isCommunityCreator(community: Community, userId: String?): Boolean {
        if (community.createdById.isBlank()) return false
        val localId = userId?.takeIf { it.isNotBlank() }
        val authUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
        return (localId != null && community.createdById == localId) ||
            (authUid != null && community.createdById == authUid)
    }

    // Notifications
    private val _notifications = MutableStateFlow<List<NotificationItem>>(
        listOf(
            NotificationItem("n1", "Class Starting Soon", "Mastering Differential Equations begins in 15 minutes.", "15m ago", false, "live"),
            NotificationItem("n2", "Task Update", "Your Thermodynamics problem has been accepted by Dr. Emmanuel Okon.", "2h ago", false, "task"),
            NotificationItem("n3", "New Message", "Dr. Emmanuel Okon sent you a new resource in private chat.", "3h ago", true, "message"),
            NotificationItem("n4", "CBT High Score!", "You scored 85% on JAMB Biology Practice Drill.", "1d ago", true, "cbt"),
            NotificationItem("n5", "Faculty of Science Hub", "Adaeze Chukwu: Shared study notes for Calculus.", "4h ago", false, "community", "comm_1"),
            NotificationItem("n6", "JAMB & UTME Master Prep", "Chukwudi Nnamdi: Live problem solving starts in 10 minutes!", "5h ago", false, "community", "comm_2"),
            NotificationItem("n7", "Engineering Scholars", "New tutorial video uploaded on Thermodynamics.", "6h ago", false, "community", "comm_3")
        )
    )
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    val filteredNotifications: Flow<List<NotificationItem>> = kotlinx.coroutines.flow.combine(
        _notifications,
        _joinedCommunityIds
    ) { notifs, joinedIds ->
        notifs.filter { item ->
            if (item.type == "community" || item.communityId != null) {
                item.communityId != null && joinedIds.contains(item.communityId)
            } else {
                true
            }
        }
    }

    // CBT Questions cache
    private val _biologyQuestions = mutableListOf<CbtQuestion>()
    private val _mathQuestions = mutableListOf<CbtQuestion>()

    init {
        loadQuestionsFromAssets()
    }

    private fun loadQuestionsFromAssets() {
        try {
            // Load Biology
            context.assets.open("questions/Biology.json").bufferedReader().use { reader ->
                val jsonStr = reader.readText()
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until minOf(jsonArray.length(), 40)) {
                    val obj = jsonArray.getJSONObject(i)
                    val qText = obj.getString("question")
                    val optObj = obj.getJSONObject("options")
                    val options = mutableListOf<String>()
                    val keys = listOf("option1", "option2", "option3", "option4", "option5")
                    for (k in keys) {
                        if (optObj.has(k)) {
                            options.add(optObj.getString(k))
                        }
                    }
                    val correctKey = obj.optString("correctOption", "option1")
                    val correctIdx = when (correctKey) {
                        "option1" -> 0
                        "option2" -> 1
                        "option3" -> 2
                        "option4" -> 3
                        "option5" -> 4
                        else -> 0
                    }
                    _biologyQuestions.add(
                        CbtQuestion(
                            id = "bio_$i",
                            subject = "Biology",
                            question = qText,
                            options = options,
                            correctIndex = correctIdx
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Provide fallback sample questions
            _biologyQuestions.addAll(getFallbackBiologyQuestions())
        }

        try {
            // Load Math
            context.assets.open("questions/math5000.json").bufferedReader().use { reader ->
                val jsonStr = reader.readText()
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until minOf(jsonArray.length(), 40)) {
                    val obj = jsonArray.getJSONObject(i)
                    val qText = obj.getString("question")
                    val optObj = obj.getJSONObject("options")
                    val options = mutableListOf<String>()
                    val keys = listOf("option1", "option2", "option3", "option4", "option5")
                    for (k in keys) {
                        if (optObj.has(k)) {
                            options.add(optObj.getString(k))
                        }
                    }
                    val correctKey = obj.optString("correctOption", "option1")
                    val correctIdx = when (correctKey) {
                        "option1" -> 0
                        "option2" -> 1
                        "option3" -> 2
                        "option4" -> 3
                        "option5" -> 4
                        else -> 0
                    }
                    _mathQuestions.add(
                        CbtQuestion(
                            id = "math_$i",
                            subject = "Mathematics",
                            question = qText,
                            options = options,
                            correctIndex = correctIdx
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _mathQuestions.addAll(getFallbackMathQuestions())
        }
    }

    // Sync Subjects with Firebase Database
    fun syncCbtSubjectsWithFirebase() {
        repoScope.launch {
            val db = firestore ?: return@launch
            try {
                val snapshot = db.collection("subjects").get().await()
                if (!snapshot.isEmpty) {
                    val loaded = snapshot.documents.mapNotNull { doc ->
                        val name = doc.getString("name") ?: return@mapNotNull null
                        CbtSubject(
                            id = doc.id,
                            name = name,
                            code = doc.getString("code") ?: "",
                            description = doc.getString("description") ?: "",
                            questionCount = (doc.getLong("questionCount") ?: 40L).toInt(),
                            iconName = doc.getString("iconName") ?: "quiz"
                        )
                    }
                    if (loaded.isNotEmpty()) {
                        _cbtSubjects.value = loaded
                    }
                } else {
                    // Seed initial subjects and question banks into Firebase Firestore
                    seedCbtSubjectsToFirebase(db)
                }
            } catch (e: Exception) {
                android.util.Log.w("SlatyRepository", "Could not sync subjects with Firebase: ${e.message}")
            }
        }
    }

    private suspend fun seedCbtSubjectsToFirebase(db: com.google.firebase.firestore.FirebaseFirestore) {
        val initialSubjects = listOf(
            CbtSubject("biology", "Biology", "BIO-JAMB", "Cell biology, genetics, ecology & physiology", 40, "biotech"),
            CbtSubject("mathematics", "Mathematics", "MTH-UTME", "Calculus, trigonometry, algebra & vectors", 40, "calculate"),
            CbtSubject("physics", "Physics", "PHY-JAMB", "Mechanics, waves, electricity & optics", 40, "bolt"),
            CbtSubject("chemistry", "Chemistry", "CHM-JAMB", "Organic, physical, stoichiometry & periodicity", 40, "science"),
            CbtSubject("english", "English Language", "ENG-UTME", "Comprehension, lexis, structure & idioms", 40, "menu_book"),
            CbtSubject("economics", "Economics", "ECN-JAMB", "Market structures, inflation & national income", 40, "trending_up"),
            CbtSubject("government", "Government", "GOV-JAMB", "Political systems, constitution & institutions", 40, "account_balance")
        )

        try {
            for (sub in initialSubjects) {
                val data = mapOf(
                    "name" to sub.name,
                    "code" to sub.code,
                    "description" to sub.description,
                    "questionCount" to sub.questionCount,
                    "iconName" to sub.iconName,
                    "createdAt" to System.currentTimeMillis()
                )
                db.collection("subjects").document(sub.id).set(data).await()

                // Seed questions subcollection
                val questions = getBaseQuestionsForSubject(sub.name)
                for (q in questions) {
                    val qData = mapOf(
                        "subject" to q.subject,
                        "question" to q.question,
                        "options" to q.options,
                        "correctIndex" to q.correctIndex
                    )
                    db.collection("subjects").document(sub.id).collection("questions").document(q.id).set(qData).await()
                }
            }
            _cbtSubjects.value = initialSubjects
            android.util.Log.i("SlatyRepository", "Successfully seeded CBT subjects into Firebase Database")
        } catch (e: Exception) {
            android.util.Log.w("SlatyRepository", "Failed to seed subjects into Firebase: ${e.message}")
        }
    }

    /**
     * Retrieves questions for a chosen subject from Firebase database.
     * The questions are continuously shuffled so they appear randomized on every selection/retake.
     */
    fun getQuestionsForSubject(subject: String): List<CbtQuestion> {
        val baseList = getBaseQuestionsForSubject(subject)
        // Continuously shuffled as requested
        return baseList.shuffled()
    }

    suspend fun fetchQuestionsFromFirebase(subject: String): List<CbtQuestion> {
        val subId = subject.lowercase(Locale.ROOT).replace(" ", "_")
        val db = firestore
        if (db != null) {
            try {
                val query = db.collection("subjects").document(subId).collection("questions").get().await()
                if (!query.isEmpty) {
                    val questions = query.documents.mapNotNull { doc ->
                        val text = doc.getString("question") ?: return@mapNotNull null
                        val options = (doc.get("options") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                        val correct = (doc.getLong("correctIndex") ?: 0L).toInt()
                        CbtQuestion(
                            id = doc.id,
                            subject = doc.getString("subject") ?: subject,
                            question = text,
                            options = options,
                            correctIndex = correct
                        )
                    }
                    if (questions.isNotEmpty()) {
                        // Continuously shuffled from Firebase
                        return questions.shuffled()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("SlatyRepository", "Failed to fetch questions from Firebase: ${e.message}")
            }
        }
        return getBaseQuestionsForSubject(subject).shuffled()
    }

    private fun getBaseQuestionsForSubject(subject: String): List<CbtQuestion> {
        val norm = subject.lowercase(Locale.ROOT)
        return when {
            norm.contains("bio") -> if (_biologyQuestions.isNotEmpty()) _biologyQuestions else getFallbackBiologyQuestions()
            norm.contains("math") -> if (_mathQuestions.isNotEmpty()) _mathQuestions else getFallbackMathQuestions()
            norm.contains("phys") -> getFallbackPhysicsQuestions()
            norm.contains("chem") -> getFallbackChemistryQuestions()
            norm.contains("eng") -> getFallbackEnglishQuestions()
            norm.contains("econ") -> getFallbackEconomicsQuestions()
            norm.contains("gov") -> getFallbackGovernmentQuestions()
            else -> if (_biologyQuestions.isNotEmpty()) _biologyQuestions else getFallbackBiologyQuestions()
        }
    }

    private fun getFallbackBiologyQuestions(): List<CbtQuestion> = listOf(
        CbtQuestion("b1", "Biology", "What type of fruit is formed from a single flower having several free carpels?", listOf("Multiple fruit", "Simple fruit", "Aggregate fruit", "Dehiscent fruit", "Indehiscent fruit"), 2),
        CbtQuestion("b2", "Biology", "Which organelle is considered the powerhouse of the cell?", listOf("Ribosome", "Endoplasmic Reticulum", "Mitochondria", "Golgi apparatus", "Nucleolus"), 2),
        CbtQuestion("b3", "Biology", "The process by which plants lose water vapor through stomata is called:", listOf("Photosynthesis", "Respiration", "Transpiration", "Osmosis", "Gutting"), 2),
        CbtQuestion("b4", "Biology", "Which of the following blood groups is known as the universal donor?", listOf("Group A", "Group B", "Group AB", "Group O", "Group Rh-only"), 3),
        CbtQuestion("b5", "Biology", "Which of the following diseases is NOT caused by a virus?", listOf("Rinderpest", "Maize rust", "Newcastle disease", "Swine fever", "Cassava mosaic"), 1)
    )

    private fun getFallbackMathQuestions(): List<CbtQuestion> = listOf(
        CbtQuestion("m1", "Mathematics", "Evaluate the integral of (3x^2 + 2x) dx from 0 to 2.", listOf("12", "16", "8", "10", "14"), 0),
        CbtQuestion("m2", "Mathematics", "If log10(x) = 3, what is the value of x?", listOf("30", "100", "1000", "10", "300"), 2),
        CbtQuestion("m3", "Mathematics", "Find the derivative of f(x) = sin(2x) with respect to x.", listOf("cos(2x)", "2 cos(2x)", "-2 cos(2x)", "-cos(2x)", "2 sin(x)"), 1),
        CbtQuestion("m4", "Mathematics", "The sum of the interior angles of a regular hexagon is:", listOf("540 degrees", "720 degrees", "360 degrees", "900 degrees", "1080 degrees"), 1),
        CbtQuestion("m5", "Mathematics", "Solve for x: 2^(x+1) = 32.", listOf("3", "4", "5", "6", "2"), 1)
    )

    private fun getFallbackPhysicsQuestions(): List<CbtQuestion> = listOf(
        CbtQuestion("p1", "Physics", "A projectile is fired with an initial velocity of 50 m/s at an angle of 30° to the horizontal. Calculate its maximum height (g = 10 m/s²).", listOf("31.25 m", "62.5 m", "125 m", "15.6 m", "45 m"), 0),
        CbtQuestion("p2", "Physics", "Which of the following is NOT an electromagnetic wave?", listOf("Gamma rays", "X-rays", "Sound waves", "Infrared waves", "Radio waves"), 2),
        CbtQuestion("p3", "Physics", "According to Newton's Second Law of Motion, force is proportional to the rate of change of:", listOf("Velocity", "Acceleration", "Momentum", "Displacement", "Kinetic energy"), 2),
        CbtQuestion("p4", "Physics", "The SI unit of electric potential difference is the:", listOf("Ampere", "Coulomb", "Ohm", "Volt", "Watt"), 3),
        CbtQuestion("p5", "Physics", "Total internal reflection occurs only when light travels from a:", listOf("Rarer to denser medium", "Denser to rarer medium", "Medium of same refractive index", "Vacuum to air", "Gas to solid"), 1)
    )

    private fun getFallbackChemistryQuestions(): List<CbtQuestion> = listOf(
        CbtQuestion("c1", "Chemistry", "The oxidation state of Manganese in KMnO4 is:", listOf("+2", "+4", "+6", "+7", "+3"), 3),
        CbtQuestion("c2", "Chemistry", "Which of the following elements has the highest electronegativity on the Pauling scale?", listOf("Oxygen", "Fluorine", "Chlorine", "Nitrogen", "Bromine"), 1),
        CbtQuestion("c3", "Chemistry", "According to Charles's Law, the volume of a fixed mass of gas at constant pressure is directly proportional to its:", listOf("Celsius temperature", "Absolute temperature", "Density", "Atmospheric pressure", "Molar mass"), 1),
        CbtQuestion("c4", "Chemistry", "What type of hybridization is found in the carbon atoms of benzene (C6H6)?", listOf("sp", "sp2", "sp3", "dsp2", "sp3d"), 1),
        CbtQuestion("c5", "Chemistry", "The characteristic functional group of all alkanols is:", listOf("-COOH", "-CHO", "-OH", "-C=O", "-NH2"), 2)
    )

    private fun getFallbackEnglishQuestions(): List<CbtQuestion> = listOf(
        CbtQuestion("e1", "English Language", "Choose the option nearest in meaning to the word in quotes: His 'ostentatious' lifestyle drew the attention of neighbors.", listOf("Modest", "Showy and pretentious", "Dangerous", "Frugal", "Secretive"), 1),
        CbtQuestion("e2", "English Language", "Neither the tutor nor the students ________ present at the seminar yesterday.", listOf("was", "were", "is", "are", "have been"), 1),
        CbtQuestion("e3", "English Language", "Select the word opposite in meaning to 'Ephemeral':", listOf("Transient", "Permanent", "Fleeting", "Fragile", "Superficial"), 1),
        CbtQuestion("e4", "English Language", "Each of the participants ________ given a certificate of attendance.", listOf("were", "was", "are", "have been", "be"), 1),
        CbtQuestion("e5", "English Language", "The idiom 'to burn the midnight oil' means to:", listOf("Waste fuel carelessly", "Study or work late into the night", "Create fire hazards", "Cook late meals", "Sleep during the day"), 1)
    )

    private fun getFallbackEconomicsQuestions(): List<CbtQuestion> = listOf(
        CbtQuestion("ec1", "Economics", "Opportunity cost is best defined as:", listOf("The monetary cost of goods", "The value of the next best alternative forgone", "The total cost of production", "The marginal cost", "Depreciation expenses"), 1),
        CbtQuestion("ec2", "Economics", "A rightward shift in the demand curve represents:", listOf("A decrease in quantity supplied", "An increase in demand at each price level", "A decrease in price", "An increase in production cost", "Perfect inelasticity"), 1),
        CbtQuestion("ec3", "Economics", "Which market structure is characterized by a single seller and no close substitutes?", listOf("Monopoly", "Oligopoly", "Monopolistic competition", "Perfect competition", "Duopoly"), 0),
        CbtQuestion("ec4", "Economics", "Inflation caused by an increase in total consumer spending is termed:", listOf("Cost-push inflation", "Demand-pull inflation", "Hyperinflation", "Structural inflation", "Imported inflation"), 1),
        CbtQuestion("ec5", "Economics", "Price elasticity of demand is mathematically measured as:", listOf("% change in price / % change in quantity", "% change in quantity demanded / % change in price", "Change in income / change in demand", "Total revenue / total quantity", "Marginal utility / price"), 1)
    )

    private fun getFallbackGovernmentQuestions(): List<CbtQuestion> = listOf(
        CbtQuestion("g1", "Government", "A system of government where constitutional power is shared between the center and constituent units is:", listOf("Unitary", "Federal", "Confederal", "Monarchical", "Oligarchical"), 1),
        CbtQuestion("g2", "Government", "The principle of separation of powers was propounded by:", listOf("John Locke", "Baron de Montesquieu", "Thomas Hobbes", "Jean-Jacques Rousseau", "A.V. Dicey"), 1),
        CbtQuestion("g3", "Government", "Which organ of government is charged with the interpretation of laws?", listOf("The Legislature", "The Executive", "The Judiciary", "The Civil Service", "The Police"), 2),
        CbtQuestion("g4", "Government", "The supreme law of a country that determines the organization of government is the:", listOf("Decree", "Constitution", "Manifesto", "Edict", "White Paper"), 1),
        CbtQuestion("g5", "Government", "In a parliamentary system of government, the Head of Government is the:", listOf("President", "Prime Minister", "Chief Justice", "Speaker of Parliament", "Governor-General"), 1)
    )

    // User Operations
    fun switchUserRole(newRole: UserRole) {
        val current = _currentUser.value ?: return
        if (newRole == UserRole.TUTOR) {
            _currentUser.value = current.copy(
                role = UserRole.TUTOR,
                name = if (current.name.startsWith("Dr.") || current.name.contains("Tutor")) current.name else "Dr. ${current.name}",
                specialization = "Senior Educator & Academic Consultant",
                hourlyRate = 4500.0
            )
        } else {
            _currentUser.value = current.copy(
                role = UserRole.STUDENT,
                name = current.name.removePrefix("Dr. ")
            )
        }
    }

    fun updateProfile(name: String, email: String, phone: String, specialization: String? = null) {
        val current = _currentUser.value ?: return
        _currentUser.value = current.copy(
            name = name,
            email = email,
            phone = phone,
            specialization = specialization ?: current.specialization
        )
    }

    fun updatePayoutDetails(bankName: String, accountNumber: String, accountName: String) {
        val current = _currentUser.value ?: return
        _currentUser.value = current.copy(
            bankName = bankName,
            accountNumber = accountNumber,
            accountName = accountName
        )
    }

    // Task Operations backed by Room-to-Firestore TaskSyncRepository
    fun createTask(
        title: String,
        courseCode: String,
        institution: String,
        faculty: String,
        department: String,
        description: String,
        amount: Double,
        deadline: String,
        learnerType: String
    ): TaskItem {
        val user = _currentUser.value
        val newTask = TaskItem(
            id = "task_${System.currentTimeMillis()}",
            title = title,
            studentName = user?.name ?: "Student",
            studentId = user?.id ?: "usr_temp",
            institution = institution,
            courseCode = courseCode,
            faculty = faculty,
            department = department,
            description = description,
            amount = amount,
            deadline = deadline,
            learnerType = learnerType,
            status = TaskStatus.PENDING
        )
        repoScope.launch {
            taskSyncRepository.createTask(newTask)
        }
        return newTask
    }

    fun acceptTask(taskId: String, tutorName: String, tutorId: String): Boolean {
        repoScope.launch {
            taskSyncRepository.acceptTask(taskId, tutorName, tutorId)
        }
        return true
    }

    fun completeTask(taskId: String): Boolean {
        repoScope.launch {
            taskSyncRepository.completeTask(taskId)
        }
        return true
    }

    suspend fun saveCbtResult(userId: String, subject: String, score: Int, total: Int) {
        val fs = firestore ?: return
        try {
            val resultId = "res_${System.currentTimeMillis()}"
            val data = hashMapOf(
                "id" to resultId,
                "userId" to userId,
                "subject" to subject,
                "score" to score,
                "total" to total,
                "percentage" to if (total > 0) ((score.toDouble() / total) * 100).toInt() else 0,
                "timestamp" to System.currentTimeMillis()
            )
            fs.collection("users").document(userId)
                .collection("results").document(resultId)
                .set(data).await()
            Log.i("SlatyRepo", "CBT Result successfully saved to Firestore: $score/$total in $subject")
        } catch (e: Exception) {
            Log.w("SlatyRepo", "Could not save CBT result to Firestore: ${e.message}")
        }
    }

    // Live Class Operations
    fun createLiveClass(
        title: String,
        fee: Double,
        subject: String = "General",
        scheduledDate: String = "Today",
        scheduledTime: String = "Starting Now",
        durationMinutes: Int = 60
    ): LiveClassSession {
        val user = _currentUser.value
        val code = com.example.data.repository.LiveClassRepository.normalizeRoomCode(title)
        val formattedSchedule = if (scheduledTime.equals("Starting Now", ignoreCase = true)) "Starting Now" else "$scheduledDate • $scheduledTime"
        val isStartingNow = scheduledTime.equals("Starting Now", ignoreCase = true)
        val newClass = LiveClassSession(
            id = "class_${System.currentTimeMillis()}",
            title = title,
            tutorName = user?.name ?: "Tutor",
            tutorId = user?.id ?: "tutor_demo",
            subject = subject,
            scheduledTime = formattedSchedule,
            durationMinutes = durationMinutes,
            fee = fee,
            attendeesCount = 1,
            roomCode = code,
            isLive = isStartingNow
        )
        _liveClasses.value = listOf(newClass) + _liveClasses.value.filter { it.roomCode != code }
        repoScope.launch {
            liveClassRepository.publishLiveClass(newClass)
        }
        return newClass
    }

    // Chat Operations
    fun getCommunityMessages(communityId: String): List<ChatMessage> {
        return _communityMessages.value[communityId] ?: emptyList()
    }

    /**
     * Real-time stream of group community messages backed by Firebase Firestore.
     * When any member posts a message, all participants in the group receive the update in real-time.
     */
    fun observeCommunityMessages(communityId: String): Flow<List<ChatMessage>> = callbackFlow {
        val fs = firestore
        val getMyId = {
            _currentUser.value?.id 
                ?: FirebaseAuth.getInstance().currentUser?.uid 
                ?: ""
        }

        if (fs == null) {
            val inMemory = _communityMessages.value[communityId] ?: emptyList()
            val myId = getMyId()
            trySend(inMemory.map { it.copy(isFromMe = it.senderId == myId) })
            val job = repoScope.launch {
                _communityMessages.collect { map ->
                    val list = map[communityId] ?: emptyList()
                    val currentId = getMyId()
                    trySend(list.map { it.copy(isFromMe = it.senderId == currentId) })
                }
            }
            awaitClose { job.cancel() }
            return@callbackFlow
        }

        var listener: ListenerRegistration? = null
        try {
            val messagesRef = fs.collection("communities")
                .document(communityId)
                .collection("messages")

            listener = messagesRef
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("SlatyRepository", "Listen to community messages failed: ${error.message}")
                        val inMemory = _communityMessages.value[communityId] ?: emptyList()
                        val myId = getMyId()
                        trySend(inMemory.map { it.copy(isFromMe = it.senderId == myId) })
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        if (snapshot.isEmpty) {
                            // If remote collection is empty, seed pre-existing community messages
                            val initial = _communityMessages.value[communityId] ?: emptyList()
                            if (initial.isNotEmpty()) {
                                repoScope.launch {
                                    try {
                                        val batch = fs.batch()
                                        initial.forEach { msg ->
                                            val docRef = messagesRef.document(msg.id)
                                            val data = hashMapOf(
                                                "id" to msg.id,
                                                "senderId" to msg.senderId,
                                                "senderName" to msg.senderName,
                                                "senderRole" to msg.senderRole.name,
                                                "text" to msg.text,
                                                "timestamp" to msg.timestamp
                                            )
                                            batch.set(docRef, data)
                                        }
                                        batch.commit().await()
                                    } catch (e: Exception) {
                                        Log.w("SlatyRepository", "Initial community message seed failed: ${e.message}")
                                    }
                                }
                                val myId = getMyId()
                                trySend(initial.map { it.copy(isFromMe = it.senderId == myId) })
                            } else {
                                trySend(emptyList())
                            }
                        } else {
                            val myId = getMyId()
                            val authUid = FirebaseAuth.getInstance().currentUser?.uid
                            val myEmail = _currentUser.value?.email ?: FirebaseAuth.getInstance().currentUser?.email ?: ""
                            val myName = _currentUser.value?.name ?: FirebaseAuth.getInstance().currentUser?.displayName ?: ""

                            val messages = snapshot.documents.mapNotNull { doc ->
                                try {
                                    val senderId = doc.getString("senderId") ?: doc.getString("userId") ?: ""
                                    val roleStr = (doc.getString("senderRole") ?: doc.getString("role") ?: "STUDENT").lowercase()
                                    val role = if (roleStr == "tutor") UserRole.TUTOR else UserRole.STUDENT
                                    val text = doc.getString("text") ?: doc.getString("message") ?: ""
                                    val timestamp = doc.getLong("timestamp") ?: doc.getLong("createdAt") ?: System.currentTimeMillis()
                                    val senderName = doc.getString("senderName") ?: doc.getString("sender") ?: doc.getString("userName") ?: doc.getString("name") ?: "Learner"

                                    val isFromMe = (myId.isNotBlank() && senderId == myId) ||
                                        (authUid != null && senderId == authUid) ||
                                        (myEmail.isNotBlank() && senderId.equals(myEmail, ignoreCase = true)) ||
                                        (myName.isNotBlank() && senderName.equals(myName, ignoreCase = true) && myId.isNotBlank())

                                    ChatMessage(
                                        id = doc.getString("id") ?: doc.id,
                                        senderId = senderId,
                                        senderName = senderName,
                                        senderRole = role,
                                        text = text,
                                        timestamp = timestamp,
                                        isFromMe = isFromMe
                                    )
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            // Keep in-memory cache synchronized as well
                            val currentMap = _communityMessages.value.toMutableMap()
                            currentMap[communityId] = messages
                            _communityMessages.value = currentMap

                            trySend(messages)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w("SlatyRepository", "Failed to setup community messages snapshot listener: ${e.message}")
            val inMemory = _communityMessages.value[communityId] ?: emptyList()
            val myId = getMyId()
            trySend(inMemory.map { it.copy(isFromMe = it.senderId == myId) })
        }

        awaitClose {
            listener?.remove()
        }
    }

    /**
     * Sends a message to the group community and writes it to Firebase Firestore so everyone in the group sees it.
     */
    fun sendCommunityMessage(communityId: String, text: String) {
        val user = _currentUser.value ?: run {
            val fUser = FirebaseAuth.getInstance().currentUser
            if (fUser != null) {
                val email = fUser.email ?: "user@slaty.edu.ng"
                val name = fUser.displayName ?: email.substringBefore("@")
                User(
                    id = fUser.uid,
                    name = name,
                    username = name.lowercase().replace(" ", "_"),
                    email = email,
                    role = UserRole.STUDENT
                )
            } else {
                User(
                    id = "usr_guest",
                    name = "Slaty Student",
                    username = "slaty_student",
                    email = "student@slaty.edu.ng",
                    role = UserRole.STUDENT
                )
            }
        }

        val msgId = "msg_${System.currentTimeMillis()}"
        val timestamp = System.currentTimeMillis()
        val msg = ChatMessage(
            id = msgId,
            senderId = user.id,
            senderName = user.name,
            senderRole = user.role,
            text = text,
            timestamp = timestamp,
            isFromMe = true
        )

        // Optimistic local update
        val currentMap = _communityMessages.value.toMutableMap()
        val list = (currentMap[communityId] ?: emptyList()).toMutableList()
        list.add(msg)
        currentMap[communityId] = list
        _communityMessages.value = currentMap

        // Real-time Firestore sync
        val fs = firestore
        if (fs != null) {
            repoScope.launch {
                try {
                    val data = hashMapOf(
                        "id" to msgId,
                        "senderId" to user.id,
                        "senderName" to user.name,
                        "senderRole" to user.role.name,
                        "text" to text,
                        "message" to text,
                        "timestamp" to timestamp,
                        "createdAt" to timestamp
                    )
                    fs.collection("communities")
                        .document(communityId)
                        .collection("messages")
                        .document(msgId)
                        .set(data)
                        .await()

                    try {
                        fs.collection("communities").document(communityId).set(
                            mapOf(
                                "lastMessage" to text,
                                "lastMessageTime" to timestamp,
                                "lastSenderId" to user.id,
                                "lastSenderName" to user.name,
                                "updatedAt" to timestamp
                            ),
                            SetOptions.merge()
                        ).await()
                    } catch (e: Exception) {
                        // ignore parent update error
                    }

                    Log.d("SlatyRepository", "Community message uploaded to Firestore for community: $communityId")
                } catch (e: Exception) {
                    Log.e("SlatyRepository", "Failed to upload community message to Firestore: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Real-time stream of 1-on-1 private chat messages with tutors/students.
     */
    fun observePrivateChatMessages(chatId: String, otherUserName: String, initialLastMessage: String): Flow<List<ChatMessage>> = callbackFlow {
        val fs = firestore
        val getMyId = {
            _currentUser.value?.id 
                ?: FirebaseAuth.getInstance().currentUser?.uid 
                ?: ""
        }

        if (fs == null) {
            val fallbackMsg = ChatMessage(
                id = "pm_init",
                senderId = "other",
                senderName = otherUserName,
                senderRole = UserRole.TUTOR,
                text = initialLastMessage,
                timestamp = System.currentTimeMillis() - 3600000,
                isFromMe = false
            )
            trySend(listOf(fallbackMsg))
            awaitClose { }
            return@callbackFlow
        }

        var listener: ListenerRegistration? = null
        try {
            val messagesRef = fs.collection("privateChats")
                .document(chatId)
                .collection("messages")

            listener = messagesRef
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("SlatyRepository", "Listen to private messages failed: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        if (snapshot.isEmpty) {
                            val seedMsg = ChatMessage(
                                id = "pm_seed",
                                senderId = "other",
                                senderName = otherUserName,
                                senderRole = UserRole.TUTOR,
                                text = initialLastMessage,
                                timestamp = System.currentTimeMillis() - 3600000,
                                isFromMe = false
                            )
                            repoScope.launch {
                                try {
                                    val data = hashMapOf(
                                        "id" to seedMsg.id,
                                        "senderId" to seedMsg.senderId,
                                        "senderName" to seedMsg.senderName,
                                        "senderRole" to seedMsg.senderRole.name,
                                        "text" to seedMsg.text,
                                        "timestamp" to seedMsg.timestamp
                                    )
                                    messagesRef.document(seedMsg.id).set(data).await()
                                } catch (e: Exception) {
                                    Log.w("SlatyRepository", "Seed private chat failed: ${e.message}")
                                }
                            }
                            trySend(listOf(seedMsg))
                        } else {
                            val myId = getMyId()
                            val authUid = FirebaseAuth.getInstance().currentUser?.uid
                            val messages = snapshot.documents.mapNotNull { doc ->
                                try {
                                    val senderId = doc.getString("senderId") ?: ""
                                    val roleStr = doc.getString("senderRole") ?: "TUTOR"
                                    val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.TUTOR }
                                    val text = doc.getString("text") ?: ""
                                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                    val senderName = doc.getString("senderName") ?: otherUserName

                                    val isFromMe = (myId.isNotBlank() && senderId == myId) ||
                                        (authUid != null && senderId == authUid)

                                    ChatMessage(
                                        id = doc.getString("id") ?: doc.id,
                                        senderId = senderId,
                                        senderName = senderName,
                                        senderRole = role,
                                        text = text,
                                        timestamp = timestamp,
                                        isFromMe = isFromMe
                                    )
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            trySend(messages)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w("SlatyRepository", "Failed to setup private messages listener: ${e.message}")
        }

        awaitClose {
            listener?.remove()
        }
    }

    /**
     * Sends a private message and syncs it with Firestore.
     */
    fun sendPrivateMessage(chatId: String, text: String, otherUserId: String, otherUserName: String, otherUserRole: UserRole) {
        val user = _currentUser.value ?: run {
            val fUser = FirebaseAuth.getInstance().currentUser
            if (fUser != null) {
                val email = fUser.email ?: "user@slaty.edu.ng"
                val name = fUser.displayName ?: email.substringBefore("@")
                User(id = fUser.uid, name = name, username = name.lowercase().replace(" ", "_"), email = email, role = UserRole.STUDENT)
            } else {
                User(id = "usr_guest", name = "Slaty Student", username = "slaty_student", email = "student@slaty.edu.ng", role = UserRole.STUDENT)
            }
        }

        val msgId = "pmsg_${System.currentTimeMillis()}"
        val timestamp = System.currentTimeMillis()

        // Real-time Firestore sync
        val fs = firestore
        if (fs != null) {
            repoScope.launch {
                try {
                    val data = hashMapOf(
                        "id" to msgId,
                        "senderId" to user.id,
                        "senderName" to user.name,
                        "senderRole" to user.role.name,
                        "text" to text,
                        "timestamp" to timestamp
                    )
                    fs.collection("privateChats")
                        .document(chatId)
                        .collection("messages")
                        .document(msgId)
                        .set(data)
                        .await()
                } catch (e: Exception) {
                    Log.e("SlatyRepository", "Failed to send private message: ${e.message}")
                }
            }
        }

        // Update last message in private chats list
        val currentChats = _privateChats.value.toMutableList()
        val idx = currentChats.indexOfFirst { it.id == chatId }
        val timeString = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
        if (idx >= 0) {
            val old = currentChats[idx]
            currentChats[idx] = old.copy(lastMessage = text, lastMessageTime = timeString)
            _privateChats.value = currentChats
        }
    }

    private fun observeCommunitiesFromFirestore() {
        val fs = firestore ?: return
        try {
            fs.collection("communities").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("SlatyRepository", "Listen to communities failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                if (snapshot.isEmpty) {
                    val defaults = _communities.value
                    repoScope.launch {
                        try {
                            defaults.forEach { comm ->
                                val data = hashMapOf(
                                    "id" to comm.id,
                                    "name" to comm.name,
                                    "description" to comm.description,
                                    "memberCount" to comm.memberCount,
                                    "category" to comm.category,
                                    "unreadCount" to comm.unreadCount,
                                    "subjects" to comm.subjects,
                                    "academicLevels" to comm.academicLevels,
                                    "createdById" to comm.createdById,
                                    "createdByName" to comm.createdByName,
                                    "createdAt" to comm.createdAt
                                )
                                fs.collection("communities").document(comm.id)
                                    .set(data, SetOptions.merge()).await()
                            }
                        } catch (e: Exception) {
                            Log.w("SlatyRepository", "Failed seeding communities: ${e.message}")
                        }
                    }
                    initialCommunitySnapshotLoaded = true
                    return@addSnapshotListener
                }

                val existingMap = _communities.value.associateBy { it.id }
                val remoteList = snapshot.documents.mapNotNull { doc ->
                    try {
                        val id = doc.getString("id") ?: doc.id
                        val name = doc.getString("name") ?: return@mapNotNull null
                        val description = doc.getString("description") ?: ""
                        val memberCount = (doc.getLong("memberCount") ?: 1L).toInt()
                        val category = doc.getString("category") ?: "General"
                        @Suppress("UNCHECKED_CAST")
                        val subjects = (doc.get("subjects") as? List<*>)
                            ?.mapNotNull { it as? String }
                            ?.takeIf { it.isNotEmpty() } ?: listOf("General")
                        @Suppress("UNCHECKED_CAST")
                        val levels = (doc.get("academicLevels") as? List<*>)
                            ?.mapNotNull { it as? String }
                            ?.takeIf { it.isNotEmpty() } ?: listOf("Undergraduate", "JAMB/UTME")
                        val createdById = doc.getString("createdById") ?: ""
                        val createdByName = doc.getString("createdByName") ?: ""
                        val createdAt = doc.getLong("createdAt") ?: 0L

                        // Community Online Notifications detection for every joined member
                        val lastMsgTime = doc.getLong("lastMessageTime") ?: 0L
                        val lastMsgText = doc.getString("lastMessage") ?: ""
                        val lastSenderId = doc.getString("lastSenderId") ?: ""
                        val lastSenderName = doc.getString("lastSenderName") ?: "Member"
                        var unreadCount = existingMap[id]?.unreadCount
                            ?: (doc.getLong("unreadCount") ?: 0L).toInt()

                        if (!initialCommunitySnapshotLoaded) {
                            if (lastMsgTime > 0L) {
                                lastNotifiedCommunityMsgTime[id] = lastMsgTime
                            }
                        } else if (lastMsgTime > 0L) {
                            val prevTime = lastNotifiedCommunityMsgTime[id] ?: 0L
                            if (lastMsgTime > prevTime) {
                                lastNotifiedCommunityMsgTime[id] = lastMsgTime
                                val isOnlineJoinedMember = _currentUser.value != null &&
                                    _joinedCommunityIds.value.contains(id)
                                if (isOnlineJoinedMember &&
                                    lastSenderId.isNotBlank() &&
                                    !isCurrentActor(lastSenderId) &&
                                    lastMsgText.isNotBlank()
                                ) {
                                    if (_activeCommunityChatId.value != id) {
                                        unreadCount += 1
                                    }
                                    val notifId = "notif_${id}_${lastMsgTime}"
                                    val newNotif = NotificationItem(
                                        id = notifId,
                                        title = name,
                                        message = "$lastSenderName: $lastMsgText",
                                        timeAgo = "Just now",
                                        isRead = false,
                                        type = "community",
                                        communityId = id
                                    )
                                    val currentNotifs = _notifications.value
                                    if (currentNotifs.none { it.id == notifId }) {
                                        _notifications.value = listOf(newNotif) + currentNotifs
                                    }
                                    _communityNotificationEvents.tryEmit(
                                        CommunityNotificationEvent(
                                            id = notifId,
                                            communityId = id,
                                            communityName = name,
                                            senderName = lastSenderName,
                                            messageText = lastMsgText,
                                            timestamp = lastMsgTime
                                        )
                                    )
                                }
                            }
                        }

                        Community(
                            id = id,
                            name = name,
                            description = description,
                            memberCount = memberCount,
                            category = category,
                            unreadCount = unreadCount,
                            subjects = subjects,
                            academicLevels = levels,
                            createdById = createdById,
                            createdByName = createdByName,
                            createdAt = createdAt
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                initialCommunitySnapshotLoaded = true
                if (remoteList.isNotEmpty()) {
                    _communities.value = remoteList.sortedByDescending { it.createdAt }
                }
            }
        } catch (e: Exception) {
            Log.w("SlatyRepository", "Failed to attach communities listener: ${e.message}")
        }
    }

    private fun observeTutorsFromFirestore() {
        val fs = firestore ?: return
        try {
            fs.collection("tutors").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("SlatyRepository", "Listen to tutors failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                if (snapshot.isEmpty) {
                    val defaults = _tutors.value
                    repoScope.launch {
                        try {
                            defaults.forEach { t ->
                                val data = hashMapOf(
                                    "id" to t.id,
                                    "name" to t.name,
                                    "username" to t.username,
                                    "specialization" to t.specialization,
                                    "rating" to t.rating,
                                    "reviewsCount" to t.reviewsCount,
                                    "hourlyRate" to t.hourlyRate,
                                    "bio" to t.bio,
                                    "subjects" to t.subjects,
                                    "verified" to t.verified,
                                    "avatarResName" to t.avatarResName,
                                    "location" to t.location,
                                    "academicLevels" to t.academicLevels
                                )
                                fs.collection("tutors").document(t.id)
                                    .set(data, SetOptions.merge()).await()
                            }
                        } catch (e: Exception) {
                            Log.w("SlatyRepository", "Failed seeding tutors: ${e.message}")
                        }
                    }
                    return@addSnapshotListener
                }

                val remoteTutors = snapshot.documents.mapNotNull { doc ->
                    try {
                        val id = doc.getString("id") ?: doc.id
                        val name = doc.getString("name") ?: return@mapNotNull null
                        @Suppress("UNCHECKED_CAST")
                        val subjects = (doc.get("subjects") as? List<*>)
                            ?.mapNotNull { it as? String }
                            ?.takeIf { it.isNotEmpty() } ?: listOf("General")
                        @Suppress("UNCHECKED_CAST")
                        val levels = (doc.get("academicLevels") as? List<*>)
                            ?.mapNotNull { it as? String }
                            ?.takeIf { it.isNotEmpty() } ?: listOf("Undergraduate", "JAMB/UTME")
                        Tutor(
                            id = id,
                            name = name,
                            username = doc.getString("username") ?: name.lowercase().replace(" ", "_"),
                            specialization = doc.getString("specialization") ?: "Verified Academic Tutor",
                            rating = doc.getDouble("rating") ?: 4.9,
                            reviewsCount = (doc.getLong("reviewsCount") ?: 0L).toInt(),
                            hourlyRate = doc.getDouble("hourlyRate") ?: 3500.0,
                            bio = doc.getString("bio") ?: "Experienced tutor helping learners master coursework.",
                            subjects = subjects,
                            verified = doc.getBoolean("verified") ?: true,
                            avatarResName = doc.getString("avatarResName") ?: "avatar_monogram",
                            location = doc.getString("location") ?: "Nigeria",
                            academicLevels = levels
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                if (remoteTutors.isNotEmpty()) {
                    val byId = remoteTutors.associateBy { it.id }
                    val merged = _tutors.value.map { local -> byId[local.id] ?: local } +
                        remoteTutors.filter { r -> _tutors.value.none { it.id == r.id } }
                    _tutors.value = merged
                }
            }
        } catch (e: Exception) {
            Log.w("SlatyRepository", "Failed to attach tutors listener: ${e.message}")
        }
    }

    fun submitTutorRating(
        tutorId: String,
        tutorName: String,
        roomCode: String,
        stars: Int,
        comment: String
    ) {
        val clampedStars = stars.coerceIn(1, 5)
        val student = _currentUser.value
        val studentId = student?.id ?: getCurrentActorId().ifBlank { "student_${System.currentTimeMillis()}" }
        val studentName = student?.name ?: "Student"

        // Resolve matching tutor from local catalog by ID or name
        val currentList = _tutors.value
        val matchedTutor = currentList.firstOrNull {
            (tutorId.isNotBlank() && it.id == tutorId) ||
                (tutorName.isNotBlank() && it.name.equals(tutorName, ignoreCase = true))
        }
        val resolvedTutorId = matchedTutor?.id ?: tutorId.ifBlank {
            "tutor_${tutorName.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_").trim('_')}"
        }
        val resolvedTutorName = matchedTutor?.name ?: tutorName.ifBlank { "Class Tutor" }

        // Optimistic update on local tutor list
        if (matchedTutor != null) {
            val (newAvg, newCount) = calculateNewAverageRating(
                currentRating = matchedTutor.rating,
                currentCount = matchedTutor.reviewsCount,
                newStars = clampedStars
            )
            _tutors.value = currentList.map {
                if (it.id == matchedTutor.id) it.copy(rating = newAvg, reviewsCount = newCount) else it
            }
        }

        // If logged in user happens to be that tutor locally, update immediately
        if (student != null && student.id == resolvedTutorId) {
            val baseCount = student.reviewsCount
            val (newAvg, newCount) = if (baseCount <= 0) {
                clampedStars.toDouble() to 1
            } else {
                calculateNewAverageRating(student.rating, baseCount, clampedStars)
            }
            _currentUser.value = student.copy(rating = newAvg, reviewsCount = newCount)
        }

        val fs = firestore ?: return
        repoScope.launch {
            try {
                val now = System.currentTimeMillis()
                val ratingDocId = "${roomCode.ifBlank { "session" }}_${resolvedTutorId}_${studentId}"
                val ratingPayload = hashMapOf(
                    "id" to ratingDocId,
                    "tutorId" to resolvedTutorId,
                    "tutorName" to resolvedTutorName,
                    "roomCode" to roomCode,
                    "studentId" to studentId,
                    "studentName" to studentName,
                    "stars" to clampedStars,
                    "comment" to comment.trim(),
                    "timestamp" to now
                )
                fs.collection("tutor_ratings").document(ratingDocId)
                    .set(ratingPayload, SetOptions.merge()).await()

                // Fetch current authoritative count/rating from tutors or users doc
                val tutorDocRef = fs.collection("tutors").document(resolvedTutorId)
                val tutorSnap = tutorDocRef.get().await()
                val userDocRef = fs.collection("users").document(resolvedTutorId)
                val userSnap = try { userDocRef.get().await() } catch (_: Exception) { null }

                val existingReviewsCount = when {
                    tutorSnap.exists() -> (tutorSnap.getLong("reviewsCount") ?: 0L).toInt()
                    userSnap != null && userSnap.exists() -> (userSnap.getLong("reviewsCount") ?: 0L).toInt()
                    matchedTutor != null -> matchedTutor.reviewsCount
                    else -> 0
                }
                val existingRating = when {
                    tutorSnap.exists() -> tutorSnap.getDouble("rating") ?: 0.0
                    userSnap != null && userSnap.exists() -> userSnap.getDouble("rating") ?: 0.0
                    matchedTutor != null -> matchedTutor.rating
                    else -> 0.0
                }

                val (computedAvg, computedCount) = if (existingReviewsCount <= 0 && matchedTutor == null) {
                    clampedStars.toDouble() to 1
                } else {
                    calculateNewAverageRating(existingRating, existingReviewsCount, clampedStars)
                }

                val tutorProfileUpdates = hashMapOf<String, Any>(
                    "id" to resolvedTutorId,
                    "name" to resolvedTutorName,
                    "rating" to computedAvg,
                    "reviewsCount" to computedCount,
                    "verified" to true,
                    "updatedAt" to now
                )
                if (!tutorSnap.exists()) {
                    tutorProfileUpdates["username"] = resolvedTutorName.lowercase(Locale.ROOT).replace(" ", "_")
                    tutorProfileUpdates["specialization"] = userSnap?.getString("specialization") ?: "Verified Academic Tutor"
                    tutorProfileUpdates["hourlyRate"] = userSnap?.getDouble("hourlyRate") ?: 3500.0
                    tutorProfileUpdates["bio"] = userSnap?.getString("bio") ?: "Verified tutor on Slaty."
                    tutorProfileUpdates["subjects"] = listOf("General")
                }
                tutorDocRef.set(tutorProfileUpdates, SetOptions.merge()).await()

                if (userSnap != null && userSnap.exists()) {
                    userDocRef.set(
                        mapOf(
                            "rating" to computedAvg,
                            "reviewsCount" to computedCount,
                            "updatedAt" to now
                        ),
                        SetOptions.merge()
                    ).await()
                }
                Log.i("SlatyRepository", "Submitted $clampedStars-star rating for $resolvedTutorName -> new avg $computedAvg ($computedCount reviews)")
            } catch (e: Exception) {
                Log.w("SlatyRepository", "Failed syncing tutor rating to Firestore: ${e.message}")
            }
        }
    }

    fun createCommunity(name: String, description: String, category: String) {
        val newCommId = "comm_${System.currentTimeMillis()}"
        val creatorId = getCurrentActorId().ifBlank { "usr_local" }
        val creatorName = _currentUser.value?.name?.ifBlank { null }
            ?: try { FirebaseAuth.getInstance().currentUser?.displayName } catch (_: Exception) { null }
            ?: "Member"
        val now = System.currentTimeMillis()
        val newComm = Community(
            id = newCommId,
            name = name,
            description = description,
            memberCount = 1,
            category = category,
            unreadCount = 0,
            createdById = creatorId,
            createdByName = creatorName,
            createdAt = now
        )
        _communities.value = listOf(newComm) + _communities.value
        val updatedJoined = _joinedCommunityIds.value + newCommId
        _joinedCommunityIds.value = updatedJoined
        joinedPrefs.edit().putStringSet("joined_ids", updatedJoined).apply()

        val fs = firestore
        if (fs != null) {
            repoScope.launch {
                try {
                    val data = hashMapOf(
                        "id" to newComm.id,
                        "name" to newComm.name,
                        "description" to newComm.description,
                        "memberCount" to newComm.memberCount,
                        "category" to newComm.category,
                        "unreadCount" to newComm.unreadCount,
                        "subjects" to newComm.subjects,
                        "academicLevels" to newComm.academicLevels,
                        "createdById" to newComm.createdById,
                        "createdByName" to newComm.createdByName,
                        "createdAt" to newComm.createdAt
                    )
                    fs.collection("communities").document(newComm.id).set(data, SetOptions.merge()).await()
                } catch (e: Exception) {
                    Log.w("SlatyRepository", "Failed to upload new community to Firestore: ${e.message}")
                }
            }
        }
    }

    fun deleteCommunity(communityId: String): Boolean {
        val target = _communities.value.firstOrNull { it.id == communityId } ?: return false
        val myUid = getCurrentActorId()
        if (!isCommunityCreator(target, myUid)) {
            return false
        }

        // Optimistic removal from UI, joined list, and notifications
        _communities.value = _communities.value.filterNot { it.id == communityId }
        val updatedJoined = _joinedCommunityIds.value - communityId
        _joinedCommunityIds.value = updatedJoined
        joinedPrefs.edit().putStringSet("joined_ids", updatedJoined).apply()
        _notifications.value = _notifications.value.filterNot { it.communityId == communityId }

        val fs = firestore
        if (fs != null) {
            repoScope.launch {
                try {
                    val msgs = fs.collection("communities")
                        .document(communityId)
                        .collection("messages")
                        .get()
                        .await()
                    if (!msgs.isEmpty) {
                        val batch = fs.batch()
                        msgs.documents.forEach { batch.delete(it.reference) }
                        batch.commit().await()
                    }
                    fs.collection("communities").document(communityId).delete().await()
                    Log.i("SlatyRepository", "Deleted community $communityId from Firestore")
                } catch (e: Exception) {
                    Log.w("SlatyRepository", "Failed deleting community from Firestore: ${e.message}")
                }
            }
        }
        return true
    }

    fun logout() {
        val uid = getCurrentActorId()
        if (uid.isNotBlank()) {
            updateOnlinePresence(uid, false)
        }
        _currentUser.value = null
    }

    fun setUser(user: User) {
        _currentUser.value = user
        updateOnlinePresence(user.id, true)
        seedPlatformDataIfEmpty(user.id, user.name)
    }

    fun seedPlatformDataIfEmpty(studentId: String = "std_demo", studentName: String = "Tobi Bakare") {
        repoScope.launch {
            try {
                val existing = studentProfileRepository.getProfile(studentId)
                if (existing == null) {
                    val profile = StudentProfile(
                        id = studentId,
                        userId = studentId,
                        fullName = studentName,
                        email = "tobi.bakare@slaty.edu.ng",
                        phoneNumber = "+234 803 123 4567",
                        institution = "University of Lagos",
                        faculty = "Science",
                        department = "Computer Science",
                        academicLevel = "300 Level",
                        matricNumber = "190407021",
                        bio = "300L Computer Science undergraduate aiming for First Class Honours in Software Systems.",
                        targetGpa = 4.8,
                        currentGpa = 4.35,
                        majorSubjects = listOf("Algorithms & Data Structures", "Database Systems", "Linear Algebra", "Computer Architecture"),
                        studyGoals = listOf("Maintain 4.5+ GPA this semester", "Complete JAMB/UTME peer mentoring sessions", "Master Dynamic Programming"),
                        totalSessionsAttended = 12,
                        totalTasksCompleted = 24
                    )
                    studentProfileRepository.saveProfile(profile)
                }

                // Seed starter learning tasks
                val sampleTasks = listOf(
                    LearningTask(
                        id = "ltask_algo_${studentId.take(6)}",
                        studentId = studentId,
                        studentName = studentName,
                        title = "Implement Shortest Path Dijkstra Algorithm",
                        description = "Complete CSC 311 assignment on graph traversal with complexity analysis and unit test cases.",
                        subject = "Computer Science",
                        courseCode = "CSC 311",
                        taskType = LearningTaskType.ASSIGNMENT,
                        priority = LearningTaskPriority.HIGH,
                        status = LearningTaskStatus.IN_PROGRESS,
                        dueDateMillis = System.currentTimeMillis() + (2 * 86400000L),
                        estimatedMinutes = 120,
                        progressPercentage = 60,
                        tags = listOf("CSC311", "Graphs", "Algorithms")
                    ),
                    LearningTask(
                        id = "ltask_db_${studentId.take(6)}",
                        studentId = studentId,
                        studentName = studentName,
                        title = "Database Normalization & Indexing Prep",
                        description = "Study 3NF and BCNF normalization concepts and practice 20 past questions.",
                        subject = "Database Systems",
                        courseCode = "CSC 315",
                        taskType = LearningTaskType.CBT_PRACTICE,
                        priority = LearningTaskPriority.MEDIUM,
                        status = LearningTaskStatus.TODO,
                        dueDateMillis = System.currentTimeMillis() + (4 * 86400000L),
                        estimatedMinutes = 90,
                        progressPercentage = 0,
                        targetScore = 90,
                        tags = listOf("CSC315", "SQL", "CBT")
                    ),
                    LearningTask(
                        id = "ltask_mth_${studentId.take(6)}",
                        studentId = studentId,
                        studentName = studentName,
                        title = "Differential Equations Past Paper Revision",
                        description = "Solve 2023 semester exam questions 1 through 4 on Laplace Transforms.",
                        subject = "Mathematics",
                        courseCode = "MTH 301",
                        taskType = LearningTaskType.EXAM_REVISION,
                        priority = LearningTaskPriority.URGENT,
                        status = LearningTaskStatus.TODO,
                        dueDateMillis = System.currentTimeMillis() + (1 * 86400000L),
                        estimatedMinutes = 75,
                        progressPercentage = 20,
                        tags = listOf("MTH301", "Calculus", "Revision")
                    )
                )
                sampleTasks.forEach { learningTaskRepository.createTask(it) }

                // Seed starter tutor sessions
                val sampleSessions = listOf(
                    TutorSession(
                        id = "sess_mth_${studentId.take(6)}",
                        title = "Advanced Differential Calculus & Vectors",
                        description = "Mastering multi-variable calculus, partial derivatives, and vector fields.",
                        tutorId = "tut_emeka_01",
                        tutorName = "Dr. Emeka Johnson",
                        studentId = studentId,
                        studentName = studentName,
                        subject = "Mathematics",
                        courseCode = "MTH 301",
                        scheduledStartTimeMillis = System.currentTimeMillis() + (8 * 3600 * 1000L),
                        durationMinutes = 90,
                        fee = 4500.0,
                        status = TutorSessionStatus.CONFIRMED,
                        locationType = SessionLocationType.ONLINE_VIDEO,
                        roomCode = "slaty-mth301-live"
                    ),
                    TutorSession(
                        id = "sess_csc_${studentId.take(6)}",
                        title = "Database Systems & Query Optimization",
                        description = "Relational algebra, query indexing, and distributed transactions.",
                        tutorId = "tut_dr_adeyemi",
                        tutorName = "Dr. Adeyemi Olumide",
                        studentId = studentId,
                        studentName = studentName,
                        subject = "Computer Science",
                        courseCode = "CSC 315",
                        scheduledStartTimeMillis = System.currentTimeMillis() + (28 * 3600 * 1000L),
                        durationMinutes = 60,
                        fee = 4000.0,
                        status = TutorSessionStatus.SCHEDULED,
                        locationType = SessionLocationType.ONLINE_VIDEO,
                        roomCode = "slaty-csc315-live"
                    )
                )
                sampleSessions.forEach { tutorSessionRepository.bookSession(it) }
            } catch (e: Exception) {
                Log.w("SlatyRepository", "Notice seeding learning platform models: ${e.message}")
            }
        }
    }

    fun login(email: String, role: UserRole) {
        val displayName = if (email.contains("@")) {
            email.substringBefore("@").replace(".", " ")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        } else {
            email
        }
        _currentUser.value = User(
            id = "user_${System.currentTimeMillis()}",
            name = if (role == UserRole.TUTOR) "Dr. $displayName" else displayName,
            username = displayName.lowercase(Locale.ROOT).replace(" ", "_"),
            email = email,
            role = role,
            specialization = if (role == UserRole.TUTOR) "Professional Instructor" else null,
            bankName = "Access Bank",
            accountNumber = "0123456789",
            accountName = displayName
        )
    }
}
