package com.example.ui.screens.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LiveRoomParticipant
import com.example.model.LiveRoomPolicy
import com.example.model.UserRole
import com.example.ui.theme.*
import com.example.webrtc.WebRtcVideoView
import org.webrtc.EglBase
import org.webrtc.VideoTrack

/**
 * Renders a live participant video tile (remote or local) with active WebRTC video stream
 * or clean avatar placeholder, live audio/mic status, spotlight ring, and raised-hand indicator.
 */
@Composable
fun ParticipantVideoTile(
    participant: LiveRoomParticipant,
    videoTrack: VideoTrack?,
    eglBaseContext: EglBase.Context,
    connectionLabel: String?,
    isSpotlighted: Boolean,
    isLocal: Boolean,
    mirrorLocal: Boolean,
    canTutorControl: Boolean,
    isScreenShare: Boolean = false,
    onSpotlightClick: (() -> Unit)? = null,
    onMuteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isScreenShare -> SlatyMint
        isSpotlighted -> SlatyMint
        participant.isHandRaised -> Color(0xFFFF9800)
        participant.isHost -> SlatyPrimary
        else -> Color(0xFF312A4A)
    }

    val isVideoVisible = videoTrack != null &&
        (isScreenShare || (participant.isCameraEnabled && !participant.forceCameraOffByTutor))

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF131020),
        border = BorderStroke(if (isSpotlighted || isScreenShare) 2.5.dp else 1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (canTutorControl && !isLocal && onSpotlightClick != null) {
                    Modifier.clickable { onSpotlightClick() }
                } else Modifier
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isVideoVisible) {
                WebRtcVideoView(
                    videoTrack = videoTrack,
                    eglBaseContext = eglBaseContext,
                    mirror = isLocal && mirrorLocal && !isScreenShare,
                    isOverlay = false,
                    scaleFit = isScreenShare,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Dark radial gradient avatar card when camera is off or stream is negotiating audio-first
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF1D1831), Color(0xFF100E1B))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(
                                    if (participant.isHost) SlatyPrimary else Color(0xFF2D274A)
                                )
                                .border(2.dp, borderColor.copy(alpha = 0.7f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = participant.name.take(2).uppercase(),
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = when {
                                participant.forceCameraOffByTutor -> "Camera Disabled by Tutor"
                                !participant.isCameraEnabled -> "Camera Paused"
                                connectionLabel != null -> connectionLabel
                                else -> "Audio Active"
                            },
                            color = SlatyTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Top-left Host / Spotlight / Hand Raise status chips
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (participant.isHost || participant.role == UserRole.TUTOR) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xCC0D0B18),
                        border = BorderStroke(1.dp, SlatyMint)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = "Tutor Host",
                                tint = SlatyMint,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "TUTOR HOST",
                                color = SlatyMint,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                if (isScreenShare) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SlatyMint
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ScreenShare,
                                contentDescription = "Presenting Screen",
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "PRESENTING SCREEN",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                } else if (isSpotlighted) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SlatyMint
                    ) {
                        Text(
                            text = "ON STAGE",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                if (participant.isHandRaised) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xEEFF9800)
                    ) {
                        Text(
                            text = "✋ HAND UP",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Top-right quick mute control for Tutor
            if (canTutorControl && !isLocal && onMuteClick != null) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xCC161224),
                    border = BorderStroke(1.dp, Color(0xFF413961)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onMuteClick() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (participant.isMicEnabled && !participant.forceMutedByTutor)
                                Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Mute or Unmute Student",
                            tint = if (participant.isMicEnabled && !participant.forceMutedByTutor)
                                SlatyMint else Color(0xFFFF6B6B),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // Bottom name plate + live WebRTC connection indicator + Mic status
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xD9090812))
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = participant.name + if (isLocal) " (You)" else "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!connectionLabel.isNullOrBlank() && !isLocal) {
                        Text(
                            text = connectionLabel,
                            color = if (connectionLabel == "Live Audio & Video") SlatyMint else SlatyTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                val micOn = participant.isMicEnabled && !participant.forceMutedByTutor
                Surface(
                    shape = CircleShape,
                    color = if (micOn) Color(0x3340E0D0) else Color(0x66FF3B30),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (micOn) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (micOn) "Microphone On" else "Microphone Muted",
                            tint = if (micOn) SlatyMint else Color(0xFFFF6B6B),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tutor Supreme Control Center Modal Sheet giving the host complete authority over room access,
 * all student mics/cameras, chat/whiteboard permissions, spotlighting, and ending class.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorSupremeControlsSheet(
    policy: LiveRoomPolicy,
    participants: List<LiveRoomParticipant>,
    localUserId: String,
    isScreenSharing: Boolean = false,
    onOpenScreenShare: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onMuteAllStudents: () -> Unit,
    onToggleAllowStudentMics: (Boolean) -> Unit,
    onToggleAllowStudentCameras: (Boolean) -> Unit,
    onToggleAllowChat: (Boolean) -> Unit,
    onToggleAllowWhiteboard: (Boolean) -> Unit,
    onToggleLockRoom: (Boolean) -> Unit,
    onGrantSpeakPermission: (LiveRoomParticipant, Boolean) -> Unit,
    onMuteParticipant: (LiveRoomParticipant, Boolean) -> Unit,
    onSpotlightParticipant: (String?) -> Unit,
    onKickParticipant: (LiveRoomParticipant) -> Unit,
    onEndClassForAll: () -> Unit
) {
    var confirmEndClass by remember { mutableStateOf(false) }
    val students = remember(participants, localUserId) {
        participants.filter { it.userId != localUserId && !it.isHost && !it.isKicked }
    }
    val raisedHands = remember(students) {
        students.filter { it.isHandRaised }
    }

    if (confirmEndClass) {
        AlertDialog(
            onDismissRequest = { confirmEndClass = false },
            containerColor = Color(0xFF1A1728),
            title = {
                Text("End Live Class for Everyone?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "All connected students will be disconnected and this live classroom will be closed.",
                    color = SlatyTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmEndClass = false
                        onEndClassForAll()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SlatyError)
                ) {
                    Text("End Class Now", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEndClass = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141122),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF473E6B)) }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(SlatyMint.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Tutor Controls",
                                tint = SlatyMint
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Tutor Supreme Controls",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Enforce audio, video, board, and entry rules live",
                                color = SlatyTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Quick Power Action Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onMuteAllStudents,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2442)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("tutor_mute_all_button")
                    ) {
                        Icon(Icons.Default.MicOff, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mute All", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = { confirmEndClass = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SlatyError),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("tutor_end_class_button")
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("End Class", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            if (onOpenScreenShare != null) {
                item {
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenScreenShare()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScreenSharing) Color(0xFF1F3834) else SlatyMint
                        ),
                        border = if (isScreenSharing) BorderStroke(1.dp, SlatyMint) else null,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("tutor_controls_screen_share_button")
                    ) {
                        Icon(
                            imageVector = if (isScreenSharing) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Filled.ScreenShare,
                            contentDescription = "Share Screen or PDF Material",
                            tint = if (isScreenSharing) SlatyMint else Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isScreenSharing) "Screen Share Live — Manage / Stop" else "Share Screen or Present PDF Material",
                            color = if (isScreenSharing) SlatyMint else Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Raised Hands Queue
            if (raisedHands.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF2A1F14),
                        border = BorderStroke(1.dp, Color(0xFFFF9800)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "✋ Raised Hands (${raisedHands.size})",
                                color = Color(0xFFFFB74D),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            raisedHands.forEach { student ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = student.name,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { onGrantSpeakPermission(student, true) },
                                            colors = ButtonDefaults.buttonColors(containerColor = SlatyMint),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("Allow Mic", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { onGrantSpeakPermission(student, false) },
                                            border = BorderStroke(1.dp, Color(0xFF5A4E40)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("Lower", color = Color.White, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Classroom Policy Switches
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1B172B),
                    border = BorderStroke(1.dp, Color(0xFF2E2847)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Classroom Authority Switches",
                            color = SlatyMint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        PolicySwitchRow(
                            title = "Allow Student Microphones",
                            subtitle = "Students can speak unless individually muted",
                            checked = policy.allowStudentMic,
                            onCheckedChange = onToggleAllowStudentMics
                        )
                        HorizontalDivider(color = Color(0xFF2B2542))

                        PolicySwitchRow(
                            title = "Allow Student Cameras",
                            subtitle = "Enable student two-way live camera feeds",
                            checked = policy.allowStudentCamera,
                            onCheckedChange = onToggleAllowStudentCameras
                        )
                        HorizontalDivider(color = Color(0xFF2B2542))

                        PolicySwitchRow(
                            title = "Allow Student In-Class Chat",
                            subtitle = "Let students post chat messages and questions",
                            checked = policy.allowStudentChat,
                            onCheckedChange = onToggleAllowChat
                        )
                        HorizontalDivider(color = Color(0xFF2B2542))

                        PolicySwitchRow(
                            title = "Allow Student Lecture Board Editing",
                            subtitle = "Keep off for tutor-only whiteboard broadcast",
                            checked = policy.allowStudentWhiteboard,
                            onCheckedChange = onToggleAllowWhiteboard
                        )
                        HorizontalDivider(color = Color(0xFF2B2542))

                        PolicySwitchRow(
                            title = "Lock Classroom Entry",
                            subtitle = "Prevent new students from entering this room",
                            checked = policy.isRoomLocked,
                            onCheckedChange = onToggleLockRoom
                        )
                    }
                }
            }

            // Spotlight Host / Reset Stage
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSpotlightParticipant(localUserId) },
                        border = BorderStroke(1.dp, SlatyMint),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = SlatyMint, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Spotlight Me", color = SlatyMint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (policy.spotlightUserId != null) {
                        OutlinedButton(
                            onClick = { onSpotlightParticipant(null) },
                            border = BorderStroke(1.dp, Color(0xFF473E6B)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Clear Spotlight", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Active Students Moderation
            if (students.isNotEmpty()) {
                item {
                    Text(
                        text = "Student Moderation (${students.size})",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(students, key = { it.userId }) { student ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1C182D),
                        border = BorderStroke(1.dp, Color(0xFF2E2847)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = student.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (student.forceMutedByTutor) "Muted by you"
                                    else if (student.isMicEnabled) "Mic active" else "Mic self-muted",
                                    color = SlatyTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { onMuteParticipant(student, !student.forceMutedByTutor) }
                                ) {
                                    Icon(
                                        imageVector = if (student.forceMutedByTutor) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = "Toggle Student Mic",
                                        tint = if (student.forceMutedByTutor) Color(0xFFFF6B6B) else SlatyMint
                                    )
                                }
                                IconButton(
                                    onClick = { onSpotlightParticipant(student.userId) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CenterFocusStrong,
                                        contentDescription = "Spotlight Student",
                                        tint = if (policy.spotlightUserId == student.userId) SlatyMint else Color.White
                                    )
                                }
                                IconButton(
                                    onClick = { onKickParticipant(student) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonRemove,
                                        contentDescription = "Remove Student",
                                        tint = SlatyError
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = SlatyTextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = SlatyMint,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF342E4D)
            )
        )
    }
}

/**
 * Classroom participant card with Tutor moderation actions directly in the Participants tab.
 */
@Composable
fun ParticipantControlRow(
    participant: LiveRoomParticipant,
    isMe: Boolean,
    hasSupremeControl: Boolean,
    isSpotlighted: Boolean,
    connectionLabel: String?,
    onToggleForceMute: () -> Unit,
    onToggleForceCameraOff: () -> Unit,
    onGrantSpeak: () -> Unit,
    onToggleSpotlight: () -> Unit,
    onKick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF191626),
        border = when {
            isSpotlighted -> BorderStroke(1.5.dp, SlatyMint)
            participant.isHandRaised -> BorderStroke(1.dp, Color(0xFFFF9800))
            else -> BorderStroke(1.dp, Color(0xFF2B2540))
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (participant.role == UserRole.TUTOR) Color(0xFF21806A) else SlatyPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = participant.name.take(2).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = participant.name + if (isMe) " (You)" else "",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (participant.isHost) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "HOST",
                                    color = Color.Black,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SlatyMint)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            if (isSpotlighted) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SPOTLIGHT",
                                    color = Color.Black,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFFFD54F))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        val roleText = if (participant.role == UserRole.TUTOR) "Certified Tutor" else "Student Participant"
                        val connText = if (!isMe && !connectionLabel.isNullOrBlank()) " • $connectionLabel" else ""
                        Text(
                            text = roleText + connText,
                            color = SlatyTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Live Audio & Video hardware indicators
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (participant.isHandRaised) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFF9800).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFFFF9800))
                        ) {
                            Text(
                                text = "✋",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                    val micActive = participant.isMicEnabled && !participant.forceMutedByTutor
                    Icon(
                        imageVector = if (micActive) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Mic status",
                        tint = if (micActive) SlatyMint else Color(0xFFFF6B6B),
                        modifier = Modifier.size(18.dp)
                    )
                    val camActive = participant.isCameraEnabled && !participant.forceCameraOffByTutor
                    Icon(
                        imageVector = if (camActive) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = "Camera status",
                        tint = if (camActive) SlatyMint else Color(0xFFFF6B6B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Tutor Supreme Action Bar for non-host students
            if (hasSupremeControl && !isMe && !participant.isHost) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (participant.isHandRaised) {
                        FilledTonalButton(
                            onClick = onGrantSpeak,
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = SlatyMint),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Allow Mic", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onToggleForceMute,
                        border = BorderStroke(1.dp, if (participant.forceMutedByTutor) Color(0xFFFF6B6B) else Color(0xFF3E355E)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = if (participant.forceMutedByTutor) "Unmute" else "Mute",
                            color = if (participant.forceMutedByTutor) Color(0xFFFF6B6B) else Color.White,
                            fontSize = 11.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onToggleForceCameraOff,
                        border = BorderStroke(1.dp, if (participant.forceCameraOffByTutor) Color(0xFFFF6B6B) else Color(0xFF3E355E)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = if (participant.forceCameraOffByTutor) "Allow Cam" else "Stop Cam",
                            color = if (participant.forceCameraOffByTutor) Color(0xFFFF6B6B) else Color.White,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = onToggleSpotlight,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "Spotlight",
                            tint = if (isSpotlighted) SlatyMint else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onKick,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonRemove,
                            contentDescription = "Remove Student",
                            tint = SlatyError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Status / Gate screen when class has ended, student was kicked, or classroom is locked by tutor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassroomStatusGateScreen(
    topBarTitle: String,
    headline: String,
    description: String,
    accentColor: Color,
    isErrorIcon: Boolean = false,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        containerColor = Color(0xFF090810),
        topBar = {
            TopAppBar(
                title = { Text(topBarTitle, color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
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
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f))
                    .border(2.dp, accentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isErrorIcon) Icons.Default.Block else Icons.Default.Lock,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = headline,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = description,
                color = SlatyTextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(containerColor = SlatyPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("classroom_gate_exit_button")
            ) {
                Text("Return to Dashboard", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

/**
 * Tutor Screen-Sharing & Document Presentation Mode Picker.
 * Offers both OS-wide full-screen sharing and zero-permission PDF/Image study material presentation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenShareOptionsSheet(
    isScreenSharing: Boolean,
    hasActiveDocument: Boolean,
    onDismiss: () -> Unit,
    onShareDeviceScreen: () -> Unit,
    onPresentStudyMaterial: () -> Unit,
    onStopScreenShare: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141122),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF473E6B)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SlatyMint.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ScreenShare,
                        contentDescription = "Screen Sharing Options",
                        tint = SlatyMint
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Present Live to Students",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Share documents, PDFs, slides, or your full device screen",
                        color = SlatyTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // Option 1: Present PDF or Image Study Material
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1C182E),
                border = BorderStroke(1.dp, SlatyMint.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        onPresentStudyMaterial()
                    }
                    .testTag("present_study_material_option")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SlatyMint.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Present PDF or Image",
                            tint = SlatyMint
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasActiveDocument) "Switch PDF / Study Material" else "Present PDF or Study Material",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Open a multi-page PDF, past question sheet, or diagram and broadcast it live",
                            color = SlatyTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = SlatyMint
                    )
                }
            }

            // Option 2: Share Full Device Screen
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1C182E),
                border = BorderStroke(1.dp, Color(0xFF393257)),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        onShareDeviceScreen()
                    }
                    .testTag("share_full_screen_option")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SlatyPrimary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = "Share Device Screen",
                            tint = SlatyLavender
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Share Full Device Screen",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Broadcast any app, slide deck, or browser window in real time",
                            color = SlatyTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = SlatyTextSecondary
                    )
                }
            }

            if (isScreenSharing) {
                Button(
                    onClick = {
                        onDismiss()
                        onStopScreenShare()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SlatyError),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("stop_screen_share_sheet_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.StopScreenShare, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Presenting & Return to Camera", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
