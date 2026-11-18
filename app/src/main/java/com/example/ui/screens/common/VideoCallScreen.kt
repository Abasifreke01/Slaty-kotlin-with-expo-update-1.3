package com.example.ui.screens.common

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.repository.LiveClassRepository
import com.example.model.LiveClassMessage
import com.example.model.LiveRoomParticipant
import com.example.model.LiveRoomPolicy
import com.example.model.User
import com.example.model.UserRole
import com.example.ui.theme.*
import com.example.webrtc.ScreenShareForegroundService
import com.example.webrtc.WebRtcClassroomManager
import com.example.webrtc.WebRtcVideoView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class RoomInteractionTab(val label: String) {
    VIDEO("Live Video"),
    CHAT("In-Class Q&A"),
    PARTICIPANTS("Participants"),
    WHITEBOARD("Shared Notes")
}

enum class VideoStageLayout {
    PIP,        // Two-way classroom stage with floating live self-view PiP
    GRID,       // Multi-participant two-way video gallery grid
    FULL_SELF,  // Fullscreen live camera self-view
    SPLIT       // Split view (Remote speaker / stage + Your live camera)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCallScreen(
    title: String,
    roomCode: String,
    isHost: Boolean,
    tutorId: String = "",
    tutorName: String = "",
    currentUser: User? = null,
    liveClassRepo: LiveClassRepository? = null,
    onSubmitTutorRating: ((tutorId: String, tutorName: String, stars: Int, comment: String) -> Unit)? = null,
    onLeaveCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { liveClassRepo ?: LiveClassRepository() }

    val cleanRoom = remember(roomCode) {
        LiveClassRepository.normalizeRoomCode(roomCode)
    }

    val participantUser = remember(currentUser, isHost) {
        currentUser ?: User(
            id = "usr_${System.currentTimeMillis() % 100000}",
            name = if (isHost) "Certified Tutor" else "Student Scholar",
            username = if (isHost) "certified_tutor" else "student_scholar",
            email = "scholar@slaty.edu.ng",
            role = if (isHost) UserRole.TUTOR else UserRole.STUDENT
        )
    }
    val participantName = participantUser.name
    val sessionToken = remember(cleanRoom, participantUser.id) { System.currentTimeMillis() }

    // UI Tabs & Stage Layout
    var selectedTab by remember { mutableStateOf(RoomInteractionTab.VIDEO) }
    var stageLayout by remember { mutableStateOf(VideoStageLayout.PIP) }
    var showTutorControlsSheet by remember { mutableStateOf(false) }
    var showScreenShareSheet by remember { mutableStateOf(false) }
    var presentedDocumentUri by remember { mutableStateOf<Uri?>(null) }
    var pendingShareTitle by remember { mutableStateOf("Tutor Screen") }

    // User Media Preferences
    var userWantsCamera by remember { mutableStateOf(true) }
    var userWantsMic by remember { mutableStateOf(true) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var isSpeakerphoneOn by remember { mutableStateOf(true) }
    var isSelfViewExpanded by remember { mutableStateOf(false) }
    var allowListenOnlyMode by remember { mutableStateOf(false) }
    var hasJoinedSuccessfully by remember { mutableStateOf(false) }

    // Runtime Permissions
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCameraPermission = grants[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasAudioPermission = grants[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionsLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    // Live Firestore Classroom State
    var participants by remember { mutableStateOf<List<LiveRoomParticipant>>(emptyList()) }
    var roomPolicy by remember { mutableStateOf(LiveRoomPolicy()) }
    var inRoomMessages by remember { mutableStateOf<List<LiveClassMessage>>(emptyList()) }
    var sharedNotes by remember { mutableStateOf("") }
    var isHandRaised by remember { mutableStateOf(false) }
    var latestHandRaiseAlert by remember { mutableStateOf<String?>(null) }
    var activeFloatingReaction by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Chat & Notes drafts + In-Class Q&A Pop-Up & Rating states
    var messageText by remember { mutableStateOf("") }
    var isQuestionFilter by remember { mutableStateOf(false) }
    var markAsQuestion by remember { mutableStateOf(false) }
    var notesDraft by remember { mutableStateOf("") }
    var isNotesSaving by remember { mutableStateOf(false) }
    val seenMessageIds = remember(cleanRoom) { mutableStateListOf<String>() }
    var hasInitializedMessages by remember(cleanRoom) { mutableStateOf(false) }
    var activeQaPopup by remember(cleanRoom) { mutableStateOf<LiveClassMessage?>(null) }
    var unreadQaCount by remember(cleanRoom) { mutableIntStateOf(0) }
    var showRateTutorDialog by remember(cleanRoom) { mutableStateOf(false) }
    var hasRatedTutorThisSession by remember(cleanRoom) { mutableStateOf(false) }
    var exitAfterRatingDialog by remember(cleanRoom) { mutableStateOf(false) }

    // Determine Tutor Supreme Authority & Enforced Permissions
    val hasSupremeControl = isHost ||
        (roomPolicy.hostId.isNotBlank() && roomPolicy.hostId == participantUser.id) ||
        (participantUser.role == UserRole.TUTOR && (roomPolicy.hostId.isBlank() || roomPolicy.hostId == participantUser.id))
    val myParticipantRecord = remember(participants, participantUser.id) {
        participants.firstOrNull { it.userId == participantUser.id }
    }
    val isKickedByTutor = !hasSupremeControl &&
        (myParticipantRecord?.isKicked == true || participantUser.id in roomPolicy.kickedUserIds)
    val isClassEndedByTutor = roomPolicy.isClassEnded && !hasSupremeControl
    val isEntryBlockedByLock = !hasSupremeControl && roomPolicy.isRoomLocked && !hasJoinedSuccessfully && myParticipantRecord == null

    val isMicLockedForMe = !hasSupremeControl &&
        (myParticipantRecord?.forceMutedByTutor == true ||
            (!roomPolicy.allowStudentMic && myParticipantRecord?.canSpeakWhenLocked != true))
    val isCamLockedForMe = !hasSupremeControl &&
        (myParticipantRecord?.forceCameraOffByTutor == true || !roomPolicy.allowStudentCamera)

    val effectiveMicOn = hasAudioPermission && userWantsMic && !isMicLockedForMe
    val effectiveCameraOn = hasCameraPermission && userWantsCamera && !isCamLockedForMe

    // Observe Room Policy, Participants, Chat, Notes, Reactions
    LaunchedEffect(cleanRoom) {
        repo.observeRoomPolicy(cleanRoom).collect { policy ->
            roomPolicy = policy
        }
    }
    LaunchedEffect(cleanRoom) {
        repo.observeRoomParticipants(cleanRoom).collect { list ->
            participants = list
            val raised = list.firstOrNull { it.isHandRaised && it.userId != participantUser.id }
            latestHandRaiseAlert = if (raised != null) "${raised.name} raised their hand!" else null
        }
    }
    LaunchedEffect(cleanRoom) {
        repo.observeRoomMessages(cleanRoom).collect { msgs ->
            inRoomMessages = msgs
            if (!hasInitializedMessages) {
                seenMessageIds.addAll(msgs.map { it.id })
                hasInitializedMessages = true
            } else {
                val newIncoming = msgs.filter { msg ->
                    msg.id.isNotBlank() &&
                        msg.id !in seenMessageIds &&
                        msg.senderId != participantUser.id
                }
                if (newIncoming.isNotEmpty()) {
                    seenMessageIds.addAll(newIncoming.map { it.id })
                    if (selectedTab != RoomInteractionTab.CHAT) {
                        unreadQaCount += newIncoming.size
                    }
                    if (hasSupremeControl) {
                        val latestImportant = newIncoming.lastOrNull { it.isQuestion } ?: newIncoming.lastOrNull()
                        if (latestImportant != null && (selectedTab != RoomInteractionTab.CHAT || latestImportant.isQuestion)) {
                            activeQaPopup = latestImportant
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(activeQaPopup?.id) {
        if (activeQaPopup != null) {
            delay(9000L)
            activeQaPopup = null
        }
    }
    LaunchedEffect(isKickedByTutor) {
        if (isKickedByTutor) {
            hasJoinedSuccessfully = false
        }
    }
    LaunchedEffect(cleanRoom) {
        repo.observeSharedNotes(cleanRoom).collect { notes ->
            sharedNotes = notes
            if (!isNotesSaving) {
                notesDraft = notes
            }
        }
    }
    LaunchedEffect(cleanRoom) {
        repo.observeReactions(cleanRoom).collect { reaction ->
            activeFloatingReaction = reaction
            delay(3000)
            activeFloatingReaction = null
        }
    }

    val effectiveTutorId = remember(tutorId, roomPolicy.hostId, participants) {
        tutorId.ifBlank { roomPolicy.hostId }
            .ifBlank { participants.firstOrNull { it.isHost || it.role == UserRole.TUTOR }?.userId ?: "" }
    }
    val effectiveTutorName = remember(tutorName, roomPolicy.hostName, participants) {
        tutorName.ifBlank { roomPolicy.hostName }
            .ifBlank { participants.firstOrNull { it.isHost || it.role == UserRole.TUTOR }?.name ?: "Class Tutor" }
    }

    if (showRateTutorDialog) {
        RateTutorEndOfClassDialog(
            tutorName = effectiveTutorName,
            classTitle = title,
            confirmButtonLabel = if (exitAfterRatingDialog) "Submit & Leave" else "Submit Rating",
            dismissButtonLabel = if (exitAfterRatingDialog) "Skip & Leave" else "Cancel",
            onDismiss = {
                showRateTutorDialog = false
                if (exitAfterRatingDialog) {
                    exitAfterRatingDialog = false
                    onLeaveCall()
                }
            },
            onSubmit = { stars, comment ->
                hasRatedTutorThisSession = true
                showRateTutorDialog = false
                onSubmitTutorRating?.invoke(effectiveTutorId, effectiveTutorName, stars, comment)
                Toast.makeText(context, "Rated $effectiveTutorName $stars/5 ★", Toast.LENGTH_SHORT).show()
                if (exitAfterRatingDialog) {
                    exitAfterRatingDialog = false
                    onLeaveCall()
                }
            }
        )
    }

    // Gate checks before rendering main WebRTC classroom
    if (isKickedByTutor) {
        ClassroomStatusGateScreen(
            topBarTitle = title,
            headline = "Removed from Classroom",
            description = "The tutor has removed you from this live classroom session. If the tutor permits you to rejoin, you will automatically re-enter.",
            accentColor = SlatyError,
            isErrorIcon = true,
            onExit = onLeaveCall,
            modifier = modifier
        )
        return
    }

    if (isClassEndedByTutor) {
        ClassroomStatusGateScreen(
            topBarTitle = title,
            headline = "Live Class Has Ended",
            description = "The tutor has ended '$title'. Thank you for attending!",
            accentColor = SlatyMint,
            isErrorIcon = false,
            rateTutorLabel = "Rate $effectiveTutorName",
            onRateTutor = if (!hasRatedTutorThisSession && onSubmitTutorRating != null) {
                {
                    exitAfterRatingDialog = false
                    showRateTutorDialog = true
                }
            } else null,
            onExit = onLeaveCall,
            modifier = modifier
        )
        return
    }

    if (isEntryBlockedByLock) {
        ClassroomStatusGateScreen(
            topBarTitle = title,
            headline = "Classroom Locked by Tutor",
            description = "The tutor has temporarily locked entry to '$title'. Please check back shortly or message your tutor.",
            accentColor = Color(0xFFFF9800),
            isErrorIcon = false,
            onExit = onLeaveCall,
            modifier = modifier
        )
        return
    }

    // Permission Gate Screen if camera/audio not yet granted and not in listen-only fallback
    val canEnterClassroom = (hasCameraPermission && hasAudioPermission) || allowListenOnlyMode
    if (!canEnterClassroom) {
        Scaffold(
            containerColor = Color(0xFF090810),
            topBar = {
                TopAppBar(
                    title = { Text("Classroom Audio & Video Setup", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onLeaveCall) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF14121F))
                )
            }
        ) { padding ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(SlatyMint.copy(alpha = 0.15f))
                        .border(2.dp, SlatyMint, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoCall,
                        contentDescription = null,
                        tint = SlatyMint,
                        modifier = Modifier.size(46.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Enable Camera & Microphone",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Slaty uses live two-way audio and HD video so you can see and speak with everyone in '$title'.",
                    color = SlatyTextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        permissionsLauncher.launch(
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SlatyMint),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("grant_camera_permission_button")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Allow Camera & Microphone",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Please allow camera & mic in settings", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF453D66)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Open App Permissions in Settings", color = Color.White, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = { allowListenOnlyMode = true },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = "Join in View & Listen-Only Mode",
                        color = SlatyMint,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        return
    }

    // AudioManager focus + Android 12-16 communication device routing for two-way live voice
    DisposableEffect(isSpeakerphoneOn) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val previousMode = audioManager?.mode ?: AudioManager.MODE_NORMAL
        @Suppress("DEPRECATION")
        val previousSpeaker = audioManager?.isSpeakerphoneOn ?: false
        var focusRequest: AudioFocusRequest? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .build()
                focusRequest = req
                audioManager?.requestAudioFocus(req)
            }

            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isMicrophoneMute = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && audioManager != null) {
                val devices = audioManager.availableCommunicationDevices
                val targetDevice = if (isSpeakerphoneOn) {
                    devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                } else {
                    devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                }
                if (targetDevice != null) {
                    audioManager.setCommunicationDevice(targetDevice)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.isSpeakerphoneOn = isSpeakerphoneOn
            }
        } catch (_: Throwable) {}

        onDispose {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager?.clearCommunicationDevice()
                } else {
                    @Suppress("DEPRECATION")
                    audioManager?.isSpeakerphoneOn = previousSpeaker
                }
                audioManager?.mode = previousMode
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
                }
            } catch (_: Throwable) {}
        }
    }

    // Two-Way WebRTC Classroom Manager
    val webRtcManager = remember(cleanRoom, participantUser.id, sessionToken) {
        WebRtcClassroomManager(
            context = context,
            roomCode = cleanRoom,
            localUserId = participantUser.id,
            repo = repo,
            localSessionToken = sessionToken
        )
    }

    // Initialize local audio/video tracks whenever permissions update, then register full room presence
    LaunchedEffect(cleanRoom, participantUser.id, hasCameraPermission, hasAudioPermission) {
        webRtcManager.startLocalMedia(
            startCamera = effectiveCameraOn,
            startMic = effectiveMicOn
        )
        repo.joinRoom(cleanRoom, participantUser, hasSupremeControl || isHost, sessionToken)
        hasJoinedSuccessfully = true
    }

    // Sync local mic & camera hardware + Firestore status only AFTER joinRoom writes full participant record
    LaunchedEffect(webRtcManager, effectiveMicOn, effectiveCameraOn, hasJoinedSuccessfully) {
        webRtcManager.setLocalMicEnabled(effectiveMicOn)
        webRtcManager.setLocalCameraEnabled(effectiveCameraOn)
        if (hasJoinedSuccessfully) {
            repo.updateParticipantMediaState(cleanRoom, participantUser.id, effectiveMicOn, effectiveCameraOn)
        }
    }

    // Keep WebRTC mesh synchronized with Firestore participants
    LaunchedEffect(webRtcManager, participants) {
        webRtcManager.syncParticipants(participants)
    }

    // Dispose WebRTC resources, stop screen-share service, and remove room presence on exit
    DisposableEffect(cleanRoom, participantUser.id) {
        onDispose {
            val wasSharing = webRtcManager.isScreenSharing.value
            webRtcManager.dispose()
            ScreenShareForegroundService.stop(context)
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                if (wasSharing) {
                    repo.setScreenSharingState(cleanRoom, false, null, "")
                }
                repo.leaveRoom(cleanRoom, participantUser.id)
            }
        }
    }

    val isScreenSharing by webRtcManager.isScreenSharing.collectAsState()
    val localVideoTrack by webRtcManager.localVideoTrack.collectAsState()
    val remoteVideoTracks by webRtcManager.remoteVideoTracks.collectAsState()
    val peerConnectionStates by webRtcManager.peerConnectionStates.collectAsState()

    val stopAllScreenSharing: () -> Unit = {
        webRtcManager.stopScreenShare()
        ScreenShareForegroundService.stop(context)
        scope.launch {
            repo.setScreenSharingState(cleanRoom, false, null, "")
        }
    }

    val mediaProjectionManager = remember(context) {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            scope.launch {
                ScreenShareForegroundService.startAndAwaitReady(context, title)
                val started = webRtcManager.startScreenShare(data) {
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        ScreenShareForegroundService.stop(context)
                        repo.setScreenSharingState(cleanRoom, false, null, "")
                    }
                }
                if (started) {
                    repo.setScreenSharingState(cleanRoom, true, participantUser.id, pendingShareTitle)
                    Toast.makeText(context, "Live screen sharing started", Toast.LENGTH_SHORT).show()
                } else {
                    ScreenShareForegroundService.stop(context)
                    Toast.makeText(context, "Unable to start screen capture on this device", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Screen share permission cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    val launchMediaProjectionPrompt: () -> Unit = {
        val manager = mediaProjectionManager
        if (manager != null) {
            mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
        } else {
            Toast.makeText(context, "Screen capture is unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {}
            val docName = resolveVideoCallDocName(context, uri)
            presentedDocumentUri = uri
            pendingShareTitle = docName
            selectedTab = RoomInteractionTab.VIDEO
            if (isScreenSharing) {
                scope.launch {
                    repo.setScreenSharingState(cleanRoom, true, participantUser.id, docName)
                }
            } else {
                launchMediaProjectionPrompt()
            }
        }
    }

    val remoteParticipants = remember(participants, participantUser.id) {
        participants.filter { it.userId != participantUser.id && !it.isKicked }
    }
    val raisedHandsCount = remember(remoteParticipants) {
        remoteParticipants.count { it.isHandRaised }
    }

    // Resolve Main Stage speaker: spotlighted peer -> Tutor Host -> first remote peer
    val primaryRemoteSpeaker = remember(remoteParticipants, roomPolicy.spotlightUserId) {
        val spotlighted = roomPolicy.spotlightUserId?.let { spotId ->
            remoteParticipants.firstOrNull { it.userId == spotId }
        }
        spotlighted
            ?: remoteParticipants.firstOrNull { it.isHost || it.role == UserRole.TUTOR }
            ?: remoteParticipants.firstOrNull()
    }

    val localSelfParticipant = remember(participantUser, hasSupremeControl, isHandRaised, effectiveMicOn, effectiveCameraOn, myParticipantRecord) {
        LiveRoomParticipant(
            userId = participantUser.id,
            name = participantName,
            role = participantUser.role,
            isHost = hasSupremeControl,
            isHandRaised = isHandRaised,
            isMicEnabled = effectiveMicOn,
            isCameraEnabled = effectiveCameraOn,
            forceMutedByTutor = myParticipantRecord?.forceMutedByTutor ?: false,
            forceCameraOffByTutor = myParticipantRecord?.forceCameraOffByTutor ?: false
        )
    }

    // Tutor Supreme Controls Bottom Sheet
    if (showTutorControlsSheet && hasSupremeControl) {
        TutorSupremeControlsSheet(
            policy = roomPolicy,
            participants = participants,
            localUserId = participantUser.id,
            isScreenSharing = isScreenSharing,
            onOpenScreenShare = { showScreenShareSheet = true },
            onDismiss = { showTutorControlsSheet = false },
            onMuteAllStudents = {
                scope.launch {
                    repo.muteAllStudents(cleanRoom)
                    Toast.makeText(context, "All students muted", Toast.LENGTH_SHORT).show()
                }
            },
            onToggleAllowStudentMics = { allow ->
                scope.launch { repo.setAllStudentsMicAllowed(cleanRoom, allow) }
            },
            onToggleAllowStudentCameras = { allow ->
                scope.launch { repo.setAllStudentsCameraAllowed(cleanRoom, allow) }
            },
            onToggleAllowChat = { allow ->
                scope.launch { repo.setStudentChatAllowed(cleanRoom, allow) }
            },
            onToggleAllowWhiteboard = { allow ->
                scope.launch { repo.setStudentWhiteboardAllowed(cleanRoom, allow) }
            },
            onToggleLockRoom = { locked ->
                scope.launch { repo.setRoomLocked(cleanRoom, locked) }
            },
            onGrantSpeakPermission = { student, allow ->
                scope.launch {
                    repo.grantSpeakingPermission(cleanRoom, student.userId, allow)
                    if (allow) {
                        Toast.makeText(context, "${student.name} can now speak", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onMuteParticipant = { student, mute ->
                scope.launch { repo.muteParticipant(cleanRoom, student.userId, mute) }
            },
            onSpotlightParticipant = { uid ->
                scope.launch {
                    repo.setSpotlightParticipant(cleanRoom, uid)
                    Toast.makeText(
                        context,
                        if (uid == null) "Spotlight cleared" else "Spotlight updated for everyone",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onKickParticipant = { student ->
                scope.launch {
                    repo.kickParticipant(cleanRoom, student.userId, student.name)
                    Toast.makeText(context, "${student.name} removed from class", Toast.LENGTH_SHORT).show()
                }
            },
            onAllowRejoin = { targetUid ->
                scope.launch {
                    repo.allowParticipantRejoin(cleanRoom, targetUid)
                    Toast.makeText(context, "Student can now rejoin the classroom", Toast.LENGTH_SHORT).show()
                }
            },
            onEndClassForAll = {
                showTutorControlsSheet = false
                webRtcManager.stopScreenShare()
                ScreenShareForegroundService.stop(context)
                scope.launch {
                    repo.setScreenSharingState(cleanRoom, false, null, "")
                    repo.endClassForAll(cleanRoom)
                    Toast.makeText(context, "Live class ended for all participants", Toast.LENGTH_SHORT).show()
                    onLeaveCall()
                }
            }
        )
    }

    if (showScreenShareSheet && hasSupremeControl) {
        ScreenShareOptionsSheet(
            isScreenSharing = isScreenSharing,
            hasActiveDocument = presentedDocumentUri != null,
            onDismiss = { showScreenShareSheet = false },
            onShareDeviceScreen = {
                presentedDocumentUri = null
                pendingShareTitle = "Tutor Device Screen"
                launchMediaProjectionPrompt()
            },
            onPresentStudyMaterial = {
                documentPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
            },
            onStopScreenShare = {
                presentedDocumentUri = null
                stopAllScreenSharing()
                Toast.makeText(context, "Screen sharing stopped", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Active Live Video & Audio Classroom Scaffold
    Scaffold(
        containerColor = Color(0xFF090810),
        topBar = {
            Surface(
                color = Color(0xFF14121F),
                border = BorderStroke(1.dp, Color(0xFF28233D))
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF3B30))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LIVE • ${maxOf(participants.size, 1)} IN CLASS" +
                                        if (roomPolicy.isRoomLocked) " • LOCKED" else "",
                                    color = Color(0xFFFF4D4D),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                )
                            }
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Speakerphone / Audio output toggle
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF221D34),
                                border = BorderStroke(1.dp, Color(0xFF383152)),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        isSpeakerphoneOn = !isSpeakerphoneOn
                                        Toast.makeText(
                                            context,
                                            if (isSpeakerphoneOn) "Speakerphone ON" else "Earpiece Mode",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .testTag("speaker_toggle_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isSpeakerphoneOn)
                                            Icons.AutoMirrored.Filled.VolumeUp
                                        else Icons.AutoMirrored.Filled.VolumeOff,
                                        contentDescription = "Toggle Speakerphone",
                                        tint = if (isSpeakerphoneOn) SlatyMint else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Top-bar Tutor Screen Share & PDF Presentation Trigger
                            if (hasSupremeControl) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (isScreenSharing) SlatyMint else SlatyPrimary.copy(alpha = 0.25f),
                                    border = BorderStroke(1.dp, if (isScreenSharing) SlatyMint else SlatyLavender),
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 36.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable { showScreenShareSheet = true }
                                        .testTag("top_share_screen_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isScreenSharing) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Filled.ScreenShare,
                                            contentDescription = "Share Screen or Document",
                                            tint = if (isScreenSharing) Color.Black else SlatyMint,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isScreenSharing) "Presenting" else "Share",
                                            color = if (isScreenSharing) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }

                            // Tutor Supreme Controls Trigger
                            if (hasSupremeControl) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = SlatyMint.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, SlatyMint),
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 36.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable { showTutorControlsSheet = true }
                                        .testTag("tutor_supreme_controls_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AdminPanelSettings,
                                            contentDescription = "Host Controls",
                                            tint = SlatyMint,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (raisedHandsCount > 0) "Controls ($raisedHandsCount✋)" else "Controls",
                                            color = SlatyMint,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            } else {
                                // Room code share chip for students
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color(0xFF221D34),
                                    border = BorderStroke(1.dp, Color(0xFF383152)),
                                    modifier = Modifier.clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Slaty Room Code", cleanRoom))
                                        Toast.makeText(context, "Code Copied: $cleanRoom", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Share Code", tint = SlatyMint, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(cleanRoom.take(14), color = SlatyMint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    // Tutor-Enforced Student Status Strip
                    if (!hasSupremeControl && (isMicLockedForMe || isCamLockedForMe)) {
                        Surface(
                            color = Color(0xFF2B1C14),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                val msg = when {
                                    isMicLockedForMe && isCamLockedForMe -> "Mic & camera muted by tutor • Raise hand ✋ to request speaking"
                                    isMicLockedForMe -> "Microphone muted by tutor • Raise hand ✋ to request permission"
                                    else -> "Student cameras disabled by tutor"
                                }
                                Text(text = msg, color = Color(0xFFFFCC80), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Live Screen Share Banner for Students or Presenting Tutor
                    if (isScreenSharing && hasSupremeControl) {
                        Surface(
                            color = SlatyMint.copy(alpha = 0.16f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.AutoMirrored.Filled.ScreenShare, contentDescription = null, tint = SlatyMint, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Presenting live: ${pendingShareTitle}",
                                        color = SlatyMint,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "Stop",
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            presentedDocumentUri = null
                                            stopAllScreenSharing()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    } else if (roomPolicy.isScreenSharing && roomPolicy.screenShareUserId != participantUser.id) {
                        Surface(
                            color = SlatyMint.copy(alpha = 0.14f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Cast, contentDescription = null, tint = SlatyMint, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tutor is presenting: ${roomPolicy.screenShareTitle.ifBlank { "Study Material" }}",
                                    color = SlatyMint,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Interactive Tab Navigation Bar
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        containerColor = Color.Transparent,
                        contentColor = SlatyPrimary,
                        edgePadding = 12.dp,
                        divider = {}
                    ) {
                        RoomInteractionTab.values().forEach { tab ->
                            val isSelected = selectedTab == tab
                            Tab(
                                selected = isSelected,
                                onClick = {
                                    selectedTab = tab
                                    if (tab == RoomInteractionTab.CHAT) {
                                        unreadQaCount = 0
                                    }
                                },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = tab.label,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp,
                                            color = if (isSelected) Color.White else SlatyTextSecondary
                                        )
                                        if (tab == RoomInteractionTab.PARTICIPANTS && participants.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) SlatyMint else Color(0xFF2A253F))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "${participants.size}",
                                                    color = if (isSelected) Color.Black else Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        if (tab == RoomInteractionTab.CHAT && (unreadQaCount > 0 || inRoomMessages.isNotEmpty())) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            if (unreadQaCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFFFF9800))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "$unreadQaCount",
                                                        color = Color.Black,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(SlatyPrimary)
                                                        .size(6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Live Audio/Video Control Dock
            Surface(
                color = Color(0xFF13111E),
                border = BorderStroke(1.dp, Color(0xFF28233D)),
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    // Hand Raise Alert Banner for Tutor & Class
                    latestHandRaiseAlert?.let { alert ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF2E2214),
                            border = BorderStroke(1.dp, Color(0xFFFF9800)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(text = "✋", fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = alert,
                                        color = Color(0xFFFFB74D),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (hasSupremeControl) {
                                    TextButton(
                                        onClick = { showTutorControlsSheet = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text("Respond", color = SlatyMint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    TextButton(
                                        onClick = { latestHandRaiseAlert = null },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Dismiss", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Camera Toggle
                        DockActionButton(
                            icon = if (effectiveCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            label = if (effectiveCameraOn) "Cam" else "Cam Off",
                            containerColor = if (effectiveCameraOn) Color(0xFF27213C) else Color(0xFF552233),
                            iconTint = if (effectiveCameraOn) SlatyMint else Color(0xFFFF6B6B),
                            onClick = {
                                if (!hasCameraPermission) {
                                    permissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                } else if (isCamLockedForMe) {
                                    Toast.makeText(context, "Camera disabled by tutor", Toast.LENGTH_SHORT).show()
                                } else {
                                    userWantsCamera = !userWantsCamera
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("camera_toggle_button")
                        )

                        // Switch Front/Back Camera
                        DockActionButton(
                            icon = Icons.Default.Cameraswitch,
                            label = "Flip",
                            containerColor = Color(0xFF27213C),
                            iconTint = Color.White,
                            onClick = {
                                isFrontCamera = !isFrontCamera
                                webRtcManager.switchCamera()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("camera_flip_button")
                        )

                        // Microphone Toggle
                        DockActionButton(
                            icon = if (effectiveMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                            label = if (effectiveMicOn) "Mic" else "Muted",
                            containerColor = if (effectiveMicOn) Color(0xFF27213C) else Color(0xFF552233),
                            iconTint = if (effectiveMicOn) SlatyMint else Color(0xFFFF6B6B),
                            onClick = {
                                if (!hasAudioPermission) {
                                    permissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                } else if (isMicLockedForMe) {
                                    Toast.makeText(context, "Muted by tutor. Raise hand ✋ to request mic.", Toast.LENGTH_SHORT).show()
                                } else {
                                    userWantsMic = !userWantsMic
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mic_toggle_button")
                        )

                        // Tutor Screen Share / Document Presenter Button
                        if (hasSupremeControl) {
                            DockActionButton(
                                icon = if (isScreenSharing) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Filled.ScreenShare,
                                label = if (isScreenSharing) "Live" else "Share",
                                containerColor = if (isScreenSharing) SlatyMint else Color(0xFF1F3834),
                                iconTint = if (isScreenSharing) Color.Black else SlatyMint,
                                labelColor = SlatyMint,
                                onClick = { showScreenShareSheet = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("screen_share_button")
                            )
                        }

                        // Stage Layout Cycle (Stage -> Grid -> Split -> Full Self)
                        DockActionButton(
                            icon = when (stageLayout) {
                                VideoStageLayout.PIP -> Icons.Default.PictureInPicture
                                VideoStageLayout.GRID -> Icons.Default.GridView
                                VideoStageLayout.SPLIT -> Icons.Default.ViewAgenda
                                VideoStageLayout.FULL_SELF -> Icons.Default.Fullscreen
                            },
                            label = "View",
                            containerColor = if (stageLayout == VideoStageLayout.GRID) SlatyMint.copy(alpha = 0.22f) else Color(0xFF27213C),
                            iconTint = if (stageLayout == VideoStageLayout.GRID) SlatyMint else Color.White,
                            onClick = {
                                stageLayout = when (stageLayout) {
                                    VideoStageLayout.PIP -> VideoStageLayout.GRID
                                    VideoStageLayout.GRID -> VideoStageLayout.SPLIT
                                    VideoStageLayout.SPLIT -> VideoStageLayout.FULL_SELF
                                    VideoStageLayout.FULL_SELF -> VideoStageLayout.PIP
                                }
                                val label = when (stageLayout) {
                                    VideoStageLayout.PIP -> "Speaker Stage + Self-View"
                                    VideoStageLayout.GRID -> "Classroom Gallery Grid"
                                    VideoStageLayout.SPLIT -> "Split Screen View"
                                    VideoStageLayout.FULL_SELF -> "Fullscreen Self-View"
                                }
                                Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("view_mode_toggle_button")
                        )

                        // Hand Raise (Students) OR Tutor Controls Quick Trigger (Tutor)
                        if (hasSupremeControl) {
                            DockActionButton(
                                icon = Icons.Default.Security,
                                label = "Host",
                                containerColor = SlatyPrimary,
                                iconTint = Color.White,
                                onClick = { showTutorControlsSheet = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tutor_dock_controls_button")
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        isHandRaised = !isHandRaised
                                        scope.launch {
                                            repo.setHandRaised(cleanRoom, participantUser.id, isHandRaised)
                                        }
                                        Toast.makeText(
                                            context,
                                            if (isHandRaised) "Hand Raised ✋ (Tutor notified)" else "Hand Lowered",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .padding(vertical = 2.dp)
                                    .testTag("hand_raise_button")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isHandRaised) Color(0xFFFF9800) else Color(0xFF252038)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "✋", fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isHandRaised) "Raised" else "Hand",
                                    color = if (isHandRaised) Color(0xFFFFB74D) else SlatyTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }

                        // Leave / End Class Button
                        DockActionButton(
                            icon = Icons.Default.CallEnd,
                            label = if (hasSupremeControl) "End" else "Leave",
                            containerColor = SlatyError,
                            iconTint = Color.White,
                            labelColor = Color(0xFFFF8A80),
                            onClick = {
                                if (hasSupremeControl) {
                                    showTutorControlsSheet = true
                                } else if (!hasRatedTutorThisSession && onSubmitTutorRating != null) {
                                    exitAfterRatingDialog = true
                                    showRateTutorDialog = true
                                } else {
                                    onLeaveCall()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("leave_call_button")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                RoomInteractionTab.VIDEO -> {
                    val activeDocumentUri = presentedDocumentUri
                    if (hasSupremeControl && activeDocumentUri != null) {
                        // Tutor In-App PDF & Study Material Presentation Stage
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DocumentPresenterPanel(
                                documentUri = activeDocumentUri,
                                isScreenSharingLive = isScreenSharing,
                                onChangeDocument = {
                                    documentPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                                },
                                onClosePresenter = {
                                    presentedDocumentUri = null
                                    if (isScreenSharing) {
                                        stopAllScreenSharing()
                                        Toast.makeText(context, "Document closed & screen share stopped", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onRequestLiveScreenShare = launchMediaProjectionPrompt,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )

                            if (remoteParticipants.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(96.dp)
                                ) {
                                    items(remoteParticipants, key = { it.userId }) { peer ->
                                        ParticipantVideoTile(
                                            participant = peer,
                                            videoTrack = remoteVideoTracks[peer.userId],
                                            eglBaseContext = webRtcManager.eglBase.eglBaseContext,
                                            connectionLabel = peerConnectionStates[peer.userId],
                                            isSpotlighted = roomPolicy.spotlightUserId == peer.userId,
                                            isLocal = false,
                                            mirrorLocal = false,
                                            canTutorControl = true,
                                            isScreenShare = false,
                                            onMuteClick = {
                                                scope.launch {
                                                    repo.muteParticipant(cleanRoom, peer.userId, !peer.forceMutedByTutor)
                                                }
                                            },
                                            modifier = Modifier
                                                .width(116.dp)
                                                .fillMaxHeight()
                                        )
                                    }
                                }
                            }
                        }
                    } else if (hasSupremeControl && isScreenSharing) {
                        // Tutor Full Device Screen Sharing Active Card (prevents Droste mirror feedback)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF141122),
                                border = BorderStroke(1.5.dp, SlatyMint),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .testTag("screen_sharing_active_card")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(84.dp)
                                            .clip(CircleShape)
                                            .background(SlatyMint.copy(alpha = 0.16f))
                                            .border(2.dp, SlatyMint, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ScreenShare,
                                            contentDescription = "Live Screen Share",
                                            tint = SlatyMint,
                                            modifier = Modifier.size(42.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Text(
                                        text = "You Are Sharing Your Screen",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "All students in Room $cleanRoom can see your screen live. Switch to any PDF, slides, or browser app—or open a study file directly inside Slaty.",
                                        color = SlatyTextSecondary,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 19.sp
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = {
                                            documentPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SlatyMint),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Open PDF or Study Material", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            stopAllScreenSharing()
                                            Toast.makeText(context, "Screen sharing stopped", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SlatyError),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("stop_screen_share_stage_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.StopScreenShare, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Stop Presenting & Return to Camera", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }

                            if (remoteParticipants.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(96.dp)
                                ) {
                                    items(remoteParticipants, key = { it.userId }) { peer ->
                                        ParticipantVideoTile(
                                            participant = peer,
                                            videoTrack = remoteVideoTracks[peer.userId],
                                            eglBaseContext = webRtcManager.eglBase.eglBaseContext,
                                            connectionLabel = peerConnectionStates[peer.userId],
                                            isSpotlighted = roomPolicy.spotlightUserId == peer.userId,
                                            isLocal = false,
                                            mirrorLocal = false,
                                            canTutorControl = true,
                                            isScreenShare = false,
                                            onMuteClick = {
                                                scope.launch {
                                                    repo.muteParticipant(cleanRoom, peer.userId, !peer.forceMutedByTutor)
                                                }
                                            },
                                            modifier = Modifier
                                                .width(116.dp)
                                                .fillMaxHeight()
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        when (stageLayout) {
                            VideoStageLayout.FULL_SELF -> {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (effectiveCameraOn && localVideoTrack != null) {
                                        WebRtcVideoView(
                                            videoTrack = localVideoTrack,
                                            eglBaseContext = webRtcManager.eglBase.eglBaseContext,
                                            mirror = isFrontCamera,
                                            isOverlay = false,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        CameraOffPlaceholder(modifier = Modifier.fillMaxSize())
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                            .align(Alignment.TopCenter),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = Color(0xCC131022),
                                            border = BorderStroke(1.dp, SlatyMint)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(if (effectiveCameraOn) SlatyMint else Color(0xFFFF5252))
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (isFrontCamera) "Live Self-View (Front)" else "Live Camera (Back)",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xCC1E1B32),
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clickable { stageLayout = VideoStageLayout.PIP }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.CloseFullscreen, contentDescription = "Exit Fullscreen", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            VideoStageLayout.GRID -> {
                                val allTiles = remember(remoteParticipants, localSelfParticipant) {
                                    remoteParticipants + localSelfParticipant
                                }
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(if (allTiles.size <= 1) 1 else 2),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(allTiles, key = { it.userId }) { p ->
                                        val isMe = p.userId == participantUser.id
                                        val track = if (isMe) localVideoTrack else remoteVideoTracks[p.userId]
                                        val tileIsScreenShare = roomPolicy.isScreenSharing && p.userId == roomPolicy.screenShareUserId
                                        ParticipantVideoTile(
                                            participant = p,
                                            videoTrack = track,
                                            eglBaseContext = webRtcManager.eglBase.eglBaseContext,
                                            connectionLabel = if (isMe) "Live Audio & Video" else peerConnectionStates[p.userId],
                                            isSpotlighted = roomPolicy.spotlightUserId == p.userId,
                                            isLocal = isMe,
                                            mirrorLocal = isFrontCamera,
                                            canTutorControl = hasSupremeControl,
                                            isScreenShare = tileIsScreenShare,
                                            onSpotlightClick = {
                                                scope.launch {
                                                    val next = if (roomPolicy.spotlightUserId == p.userId) null else p.userId
                                                    repo.setSpotlightParticipant(cleanRoom, next)
                                                }
                                            },
                                            onMuteClick = {
                                                scope.launch { repo.muteParticipant(cleanRoom, p.userId, !p.forceMutedByTutor) }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(if (allTiles.size <= 2) 320.dp else 220.dp)
                                        )
                                    }
                                }
                            }

                            VideoStageLayout.SPLIT -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    ) {
                                        if (primaryRemoteSpeaker != null) {
                                            val primaryIsScreenShare = roomPolicy.isScreenSharing &&
                                                primaryRemoteSpeaker.userId == roomPolicy.screenShareUserId
                                            ParticipantVideoTile(
                                                participant = primaryRemoteSpeaker,
                                                videoTrack = remoteVideoTracks[primaryRemoteSpeaker.userId],
                                                eglBaseContext = webRtcManager.eglBase.eglBaseContext,
                                                connectionLabel = peerConnectionStates[primaryRemoteSpeaker.userId],
                                                isSpotlighted = true,
                                                isLocal = false,
                                                mirrorLocal = false,
                                                canTutorControl = hasSupremeControl,
                                                isScreenShare = primaryIsScreenShare,
                                                onMuteClick = {
                                                    scope.launch {
                                                        repo.muteParticipant(
                                                            cleanRoom,
                                                            primaryRemoteSpeaker.userId,
                                                            !primaryRemoteSpeaker.forceMutedByTutor
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            LectureStageCard(
                                                title = title,
                                                roomCode = cleanRoom,
                                                participantCount = participants.size,
                                                sharedNotes = sharedNotes,
                                                onOpenNotes = { selectedTab = RoomInteractionTab.WHITEBOARD },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }

                                    ParticipantVideoTile(
                                        participant = localSelfParticipant,
                                        videoTrack = localVideoTrack,
                                        eglBaseContext = webRtcManager.eglBase.eglBaseContext,
                                        connectionLabel = "Live Self-View",
                                        isSpotlighted = false,
                                        isLocal = true,
                                        mirrorLocal = isFrontCamera,
                                        canTutorControl = false,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    )
                                }
                            }

                            VideoStageLayout.PIP -> {
                                // Two-Way Adaptive Classroom Stage + Floating Self-View PiP
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (primaryRemoteSpeaker != null) {
                                        val primaryIsScreenShare = roomPolicy.isScreenSharing &&
                                            primaryRemoteSpeaker.userId == roomPolicy.screenShareUserId
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Main Remote Participant Video, Screen Share, & Audio Stage
                                            ParticipantVideoTile(
                                                participant = primaryRemoteSpeaker,
                                                videoTrack = remoteVideoTracks[primaryRemoteSpeaker.userId],
                                                eglBaseContext = webRtcManager.eglBase.eglBaseContext,
                                                connectionLabel = peerConnectionStates[primaryRemoteSpeaker.userId],
                                                isSpotlighted = true,
                                                isLocal = false,
                                                mirrorLocal = false,
                                                canTutorControl = hasSupremeControl,
                                                isScreenShare = primaryIsScreenShare,
                                                onMuteClick = {
                                                    scope.launch {
                                                        repo.muteParticipant(
                                                            cleanRoom,
                                                            primaryRemoteSpeaker.userId,
                                                            !primaryRemoteSpeaker.forceMutedByTutor
                                                        )
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f)
                                            )

                                            // Secondary Participants Filmstrip (see & hear everyone!)
                                            val otherRemotes = remember(remoteParticipants, primaryRemoteSpeaker) {
                                                remoteParticipants.filter { it.userId != primaryRemoteSpeaker.userId }
                                            }
                                            if (otherRemotes.isNotEmpty()) {
                                                LazyRow(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(112.dp)
                                                ) {
                                                    items(otherRemotes, key = { it.userId }) { peer ->
                                                        val peerIsScreenShare = roomPolicy.isScreenSharing &&
                                                            peer.userId == roomPolicy.screenShareUserId
                                                        ParticipantVideoTile(
                                                            participant = peer,
                                                            videoTrack = remoteVideoTracks[peer.userId],
                                                            eglBaseContext = webRtcManager.eglBase.eglBaseContext,
                                                            connectionLabel = peerConnectionStates[peer.userId],
                                                            isSpotlighted = roomPolicy.spotlightUserId == peer.userId,
                                                            isLocal = false,
                                                            mirrorLocal = false,
                                                            canTutorControl = hasSupremeControl,
                                                            isScreenShare = peerIsScreenShare,
                                                            onSpotlightClick = {
                                                                scope.launch {
                                                                    repo.setSpotlightParticipant(cleanRoom, peer.userId)
                                                                }
                                                            },
                                                            onMuteClick = {
                                                                scope.launch {
                                                                    repo.muteParticipant(cleanRoom, peer.userId, !peer.forceMutedByTutor)
                                                                }
                                                            },
                                                            modifier = Modifier
                                                                .width(136.dp)
                                                                .fillMaxHeight()
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Floating Picture-in-Picture Local Self-View Window (setZOrderMediaOverlay = true)
                                        val viewingRemoteScreenShare = primaryIsScreenShare
                                        val pipWidth = when {
                                            isSelfViewExpanded -> 180.dp
                                            viewingRemoteScreenShare -> 104.dp
                                            else -> 126.dp
                                        }
                                        val pipHeight = when {
                                            isSelfViewExpanded -> 240.dp
                                            viewingRemoteScreenShare -> 138.dp
                                            else -> 168.dp
                                        }

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF141122),
                                        border = BorderStroke(2.dp, SlatyMint),
                                        shadowElevation = 12.dp,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(16.dp)
                                            .size(width = pipWidth, height = pipHeight)
                                            .clip(RoundedCornerShape(16.dp))
                                            .testTag("self_view_pip_container")
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            if (effectiveCameraOn && localVideoTrack != null) {
                                                WebRtcVideoView(
                                                    videoTrack = localVideoTrack,
                                                    eglBaseContext = webRtcManager.eglBase.eglBaseContext,
                                                    mirror = isFrontCamera,
                                                    isOverlay = true,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                CameraOffPlaceholder(modifier = Modifier.fillMaxSize())
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0x990A0914))
                                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "You",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(22.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0x882A2440))
                                                            .clickable {
                                                                isFrontCamera = !isFrontCamera
                                                                webRtcManager.switchCamera()
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.Cameraswitch, contentDescription = "Flip", tint = Color.White, modifier = Modifier.size(13.dp))
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .size(22.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0x882A2440))
                                                            .clickable { isSelfViewExpanded = !isSelfViewExpanded },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isSelfViewExpanded) Icons.Default.CloseFullscreen else Icons.Default.Fullscreen,
                                                            contentDescription = "Resize Self-View",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Alone in the room so far: full crystal-clear self-view + Share Room Invite banner
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (effectiveCameraOn && localVideoTrack != null) {
                                            WebRtcVideoView(
                                                videoTrack = localVideoTrack,
                                                eglBaseContext = webRtcManager.eglBase.eglBaseContext,
                                                mirror = isFrontCamera,
                                                isOverlay = false,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            CameraOffPlaceholder(modifier = Modifier.fillMaxSize())
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color(0xCC131022),
                                            border = BorderStroke(1.dp, Color(0xFF3A325C)),
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .padding(14.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text(
                                                    text = if (hasSupremeControl)
                                                        "Waiting for students to join..."
                                                    else "Waiting for tutor or classmates...",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Two-way HD audio & video starts automatically as soon as someone enters Room $cleanRoom.",
                                                    color = SlatyTextSecondary,
                                                    fontSize = 11.sp
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    if (hasSupremeControl) {
                                                        Button(
                                                            onClick = { showScreenShareSheet = true },
                                                            colors = ButtonDefaults.buttonColors(containerColor = SlatyPrimary),
                                                            shape = RoundedCornerShape(10.dp),
                                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(48.dp)
                                                                .testTag("waiting_share_screen_button")
                                                        ) {
                                                            Icon(Icons.AutoMirrored.Filled.ScreenShare, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                            Spacer(modifier = Modifier.width(5.dp))
                                                            Text("Share Screen", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                    Button(
                                                        onClick = {
                                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            clipboard.setPrimaryClip(ClipData.newPlainText("Slaty Room Code", cleanRoom))
                                                            Toast.makeText(context, "Room Code Copied: $cleanRoom", Toast.LENGTH_SHORT).show()
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = SlatyMint),
                                                        shape = RoundedCornerShape(10.dp),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(48.dp)
                                                    ) {
                                                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Copy Code", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                }

                RoomInteractionTab.CHAT -> {
                    val canChat = hasSupremeControl || roomPolicy.allowStudentChat
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !isQuestionFilter,
                                onClick = { isQuestionFilter = false },
                                label = { Text("All Chat (${inRoomMessages.size})", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SlatyPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = isQuestionFilter,
                                onClick = { isQuestionFilter = true },
                                label = { Text("Questions Only (${inRoomMessages.count { it.isQuestion }})", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFF9800),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }

                        val displayedMessages = remember(inRoomMessages, isQuestionFilter) {
                            if (isQuestionFilter) inRoomMessages.filter { it.isQuestion } else inRoomMessages
                        }

                        val listState = rememberLazyListState()
                        LaunchedEffect(displayedMessages.size) {
                            if (displayedMessages.isNotEmpty()) {
                                listState.animateScrollToItem(displayedMessages.size - 1)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (displayedMessages.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Chat", tint = Color.Gray, modifier = Modifier.size(40.dp))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = if (isQuestionFilter) "No questions asked yet." else "Live classroom chat is ready.",
                                                color = SlatyTextSecondary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(displayedMessages) { msg ->
                                    val isMe = msg.senderId == participantUser.id
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isMe) "You" else msg.senderName,
                                                color = if (msg.senderRole == UserRole.TUTOR) SlatyMint else SlatyLavender,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (msg.senderRole == UserRole.TUTOR) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "TUTOR",
                                                    color = Color.Black,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(SlatyMint)
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                            if (msg.isQuestion) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "QUESTION",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFFFF9800))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isMe) SlatyPrimary.copy(alpha = 0.85f) else Color(0xFF1E1B2C),
                                            border = if (msg.isQuestion) BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.6f)) else null,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                text = msg.text,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!canChat) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF281D1A),
                                border = BorderStroke(1.dp, Color(0xFFFF9800)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "In-class chat is currently paused by the tutor.",
                                        color = Color(0xFFFFCC80),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF1E1A2E),
                                border = BorderStroke(1.dp, Color(0xFF383152)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { markAsQuestion = !markAsQuestion },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Text(text = if (markAsQuestion) "❓" else "💬", fontSize = 16.sp)
                                    }

                                    TextField(
                                        value = messageText,
                                        onValueChange = { messageText = it },
                                        placeholder = {
                                            Text(
                                                text = if (markAsQuestion) "Ask a question..." else "Type in-class message...",
                                                color = Color.Gray,
                                                fontSize = 13.sp
                                            )
                                        },
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = {
                                            val text = messageText.trim()
                                            if (text.isNotBlank()) {
                                                scope.launch {
                                                    repo.sendRoomMessage(
                                                        cleanRoom,
                                                        LiveClassMessage(
                                                            senderId = participantUser.id,
                                                            senderName = participantName,
                                                            senderRole = participantUser.role,
                                                            text = text,
                                                            timestamp = System.currentTimeMillis(),
                                                            isQuestion = markAsQuestion
                                                        )
                                                    )
                                                    messageText = ""
                                                    markAsQuestion = false
                                                }
                                            }
                                        },
                                        enabled = messageText.isNotBlank(),
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (messageText.isNotBlank()) SlatyPrimary else Color(0xFF2C2740))
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send",
                                            tint = if (messageText.isNotBlank()) Color.White else Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                RoomInteractionTab.PARTICIPANTS -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Active Room Participants (${participants.size})",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (hasSupremeControl) {
                                    TextButton(onClick = { showTutorControlsSheet = true }) {
                                        Text("All Controls", color = SlatyMint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (participants.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1B1828),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "You are currently the only one in the room. Share room code $cleanRoom to invite others!",
                                            color = SlatyTextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        items(participants, key = { it.userId }) { p ->
                            val isMe = p.userId == participantUser.id
                            ParticipantControlRow(
                                participant = p,
                                isMe = isMe,
                                hasSupremeControl = hasSupremeControl,
                                isSpotlighted = roomPolicy.spotlightUserId == p.userId,
                                connectionLabel = peerConnectionStates[p.userId],
                                onToggleForceMute = {
                                    scope.launch { repo.muteParticipant(cleanRoom, p.userId, !p.forceMutedByTutor) }
                                },
                                onToggleForceCameraOff = {
                                    scope.launch { repo.disableParticipantCamera(cleanRoom, p.userId, !p.forceCameraOffByTutor) }
                                },
                                onGrantSpeak = {
                                    scope.launch { repo.grantSpeakingPermission(cleanRoom, p.userId, true) }
                                },
                                onToggleSpotlight = {
                                    scope.launch {
                                        val next = if (roomPolicy.spotlightUserId == p.userId) null else p.userId
                                        repo.setSpotlightParticipant(cleanRoom, next)
                                    }
                                },
                                onKick = {
                                    scope.launch {
                                        repo.kickParticipant(cleanRoom, p.userId, p.name)
                                        Toast.makeText(context, "${p.name} removed from class", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }

                RoomInteractionTab.WHITEBOARD -> {
                    val canEditBoard = hasSupremeControl || roomPolicy.allowStudentWhiteboard
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Live Shared Lecture Board",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (canEditBoard)
                                        "All notes and equations update live across connected devices"
                                    else "View-only mode • Tutor controls whiteboard broadcasting",
                                    color = SlatyTextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            if (canEditBoard) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isNotesSaving = true
                                            repo.updateSharedNotes(cleanRoom, notesDraft)
                                            isNotesSaving = false
                                            Toast.makeText(context, "Board saved & broadcast to all devices!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SlatyMint),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(text = "Broadcast", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = notesDraft,
                            onValueChange = {
                                if (canEditBoard) {
                                    notesDraft = it
                                    isNotesSaving = true
                                }
                            },
                            readOnly = !canEditBoard,
                            placeholder = {
                                Text(
                                    text = if (canEditBoard)
                                        "Write equations, formulas, derivation steps, or lecture summaries here..."
                                    else "Waiting for the tutor to broadcast lecture notes...",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF14121E),
                                unfocusedContainerColor = Color(0xFF14121E),
                                focusedBorderColor = SlatyMint,
                                unfocusedBorderColor = Color(0xFF2C2742),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                }
            }

            // Floating Animated Reaction Overlay
            activeFloatingReaction?.let { (emoji, sender) ->
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xCC171424))
                        .border(1.dp, SlatyMint, RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = emoji, fontSize = 32.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "$sender reacted",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // In-Class Q&A Pop-Up Notification for Tutor
            AnimatedVisibility(
                visible = hasSupremeControl && activeQaPopup != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                activeQaPopup?.let { popupMsg ->
                    val accent = if (popupMsg.isQuestion) Color(0xFFFF9800) else SlatyMint
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xF5191529),
                        border = BorderStroke(1.5.dp, accent),
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tutor_qa_popup")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(accent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (popupMsg.isQuestion) Icons.Default.LiveHelp else Icons.Default.QuestionAnswer,
                                    contentDescription = "New In-Class Q&A",
                                    tint = accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (popupMsg.isQuestion) "New Question • ${popupMsg.senderName}" else "In-Class Q&A • ${popupMsg.senderName}",
                                    color = accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = popupMsg.text,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            TextButton(
                                onClick = {
                                    isQuestionFilter = popupMsg.isQuestion
                                    selectedTab = RoomInteractionTab.CHAT
                                    unreadQaCount = 0
                                    activeQaPopup = null
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("tutor_qa_popup_open_button")
                            ) {
                                Text("Reply", color = SlatyMint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            IconButton(
                                onClick = { activeQaPopup = null },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("tutor_qa_popup_dismiss_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss Q&A Alert", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraOffPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0xFF1B172C)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2C2442)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VideocamOff,
                    contentDescription = "Camera Paused",
                    tint = SlatyLavender,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Camera Paused",
                color = SlatyTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun LectureStageCard(
    title: String,
    roomCode: String,
    participantCount: Int,
    sharedNotes: String,
    onOpenNotes: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF110E1E),
        border = BorderStroke(1.dp, Color(0xFF2C2642)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Interactive Lecture Broadcast",
                        color = SlatyMint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF26203D)) {
                    Text(
                        text = "${maxOf(participantCount, 1)} Online",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF151322),
                border = BorderStroke(1.dp, Color(0xFF2A2440)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .clickable { onOpenNotes() }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live Lecture Board", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Tap to open", color = SlatyMint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (sharedNotes.isNotBlank()) sharedNotes
                        else "Notes and derivations shared by the tutor appear live here.",
                        color = if (sharedNotes.isNotBlank()) Color.White else SlatyTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DockActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    iconTint: Color,
    labelColor: Color = SlatyTextSecondary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = labelColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun resolveVideoCallDocName(context: Context, uri: Uri): String {
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
    } catch (_: Throwable) {}
    return uri.lastPathSegment?.substringAfterLast('/') ?: "Study Document"
}
