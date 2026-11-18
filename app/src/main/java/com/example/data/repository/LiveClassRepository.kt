package com.example.data.repository

import android.util.Log
import com.example.model.LiveClassMessage
import com.example.model.LiveClassSession
import com.example.model.LiveRoomParticipant
import com.example.model.LiveRoomPolicy
import com.example.model.User
import com.example.model.UserRole
import com.example.model.WebRtcIceCandidatePayload
import com.example.model.WebRtcSignalChannel
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale

class LiveClassRepository(
    private val firestore: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
) {
    companion object {
        private const val TAG = "LiveClassRepo"
        private const val COLLECTION_LIVE_CLASSES = "live_classes"
        private const val COLLECTION_LIVE_ROOMS = "live_rooms"
        private const val SUBCOLLECTION_PARTICIPANTS = "participants"
        private const val SUBCOLLECTION_MESSAGES = "messages"
        private const val SUBCOLLECTION_SHARED = "shared"
        private const val SUBCOLLECTION_REACTIONS = "reactions"
        private const val SUBCOLLECTION_SIGNALS = "signals"
        private const val SUBCOLLECTION_ICE_CANDIDATES = "ice_candidates"

        /**
         * Canonicalizes links, titles, or IDs into the exact same Firestore room code
         * so Tutor and Students always land in the same live audio/video room.
         */
        fun normalizeRoomCode(raw: String): String {
            val trimmed = raw.trim()
                .removePrefix("https://meet.jit.si/")
                .removePrefix("http://meet.jit.si/")
                .removePrefix("slaty://class/")
                .substringBefore("?")
                .substringBefore("#")
                .trim('/')
                .lowercase(Locale.ROOT)
            val sanitized = trimmed.replace(Regex("[^a-z0-9-]+"), "-").trim('-')
            if (sanitized.isBlank()) return "slaty-general-live"
            return if (sanitized.startsWith("slaty-")) sanitized else "slaty-$sanitized"
        }

        fun buildChannelId(uidA: String, uidB: String): String {
            return if (uidA <= uidB) "${uidA}__${uidB}" else "${uidB}__${uidA}"
        }
    }

    /**
     * Publishes a live class to Firestore so all connected devices can immediately discover it.
     */
    suspend fun publishLiveClass(session: LiveClassSession) {
        val fs = firestore ?: return
        try {
            val canonicalRoom = normalizeRoomCode(session.roomCode)
            val data = hashMapOf(
                "id" to session.id,
                "title" to session.title,
                "tutorName" to session.tutorName,
                "tutorId" to session.tutorId,
                "subject" to session.subject,
                "scheduledTime" to session.scheduledTime,
                "durationMinutes" to session.durationMinutes,
                "fee" to session.fee,
                "attendeesCount" to session.attendeesCount,
                "roomCode" to canonicalRoom,
                "isLive" to session.isLive,
                "createdAt" to System.currentTimeMillis()
            )
            fs.collection(COLLECTION_LIVE_CLASSES).document(session.id).set(data, SetOptions.merge()).await()
            Log.i(TAG, "Published live class: ${session.title} ($canonicalRoom)")
        } catch (e: Exception) {
            Log.w(TAG, "Error publishing live class: ${e.message}")
        }
    }

    /**
     * Real-time stream of all active live classes synced across devices.
     */
    fun observeLiveClasses(): Flow<List<LiveClassSession>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        var listener: ListenerRegistration? = null
        try {
            listener = fs.collection(COLLECTION_LIVE_CLASSES)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Listen to live classes failed", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val classesWithCreated = snapshot.documents.mapNotNull { doc ->
                            try {
                                val createdAt = doc.getLong("createdAt") ?: 0L
                                val session = LiveClassSession(
                                    id = doc.getString("id") ?: doc.id,
                                    title = doc.getString("title") ?: "Live Class",
                                    tutorName = doc.getString("tutorName") ?: "Certified Tutor",
                                    tutorId = doc.getString("tutorId") ?: "",
                                    subject = doc.getString("subject") ?: "General",
                                    scheduledTime = doc.getString("scheduledTime") ?: "Live Now",
                                    durationMinutes = (doc.getLong("durationMinutes") ?: 60L).toInt(),
                                    fee = doc.getDouble("fee") ?: 2500.0,
                                    attendeesCount = (doc.getLong("attendeesCount") ?: 1L).toInt(),
                                    roomCode = doc.getString("roomCode") ?: "slaty-live",
                                    isLive = doc.getBoolean("isLive") ?: true
                                )
                                createdAt to session
                            } catch (e: Exception) {
                                null
                            }
                        }
                        trySend(classesWithCreated.sortedByDescending { it.first }.map { it.second })
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach live classes listener: ${e.message}")
        }

        awaitClose {
            listener?.remove()
        }
    }

    /**
     * Registers a participant joining the live room and initializes Host room policy if host.
     */
    suspend fun joinRoom(roomCode: String, user: User, isHost: Boolean, sessionToken: Long = System.currentTimeMillis()) {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            if (isHost) {
                val roomPolicyUpdate = hashMapOf<String, Any>(
                    "hostId" to user.id,
                    "hostName" to user.name,
                    "isClassEnded" to false,
                    "updatedAt" to System.currentTimeMillis()
                )
                fs.collection(COLLECTION_LIVE_ROOMS)
                    .document(canonicalRoom)
                    .set(roomPolicyUpdate, SetOptions.merge())
                    .await()
            }

            val participantData = hashMapOf(
                "userId" to user.id,
                "name" to user.name,
                "role" to user.role.name,
                "isHost" to isHost,
                "isHandRaised" to false,
                "isMicEnabled" to true,
                "isCameraEnabled" to true,
                "forceMutedByTutor" to false,
                "forceCameraOffByTutor" to false,
                "canSpeakWhenLocked" to isHost,
                "isKicked" to false,
                "sessionToken" to sessionToken,
                "joinedAt" to System.currentTimeMillis()
            )
            fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_PARTICIPANTS)
                .document(user.id)
                .set(participantData, SetOptions.merge())
                .await()
            Log.i(TAG, "User ${user.name} (${user.id}) joined live room $canonicalRoom (host=$isHost)")
        } catch (e: Exception) {
            Log.w(TAG, "Error registering room participant: ${e.message}")
        }
    }

    /**
     * Unregisters a participant leaving the live room.
     */
    suspend fun leaveRoom(roomCode: String, userId: String) {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_PARTICIPANTS)
                .document(userId)
                .delete()
                .await()
            Log.i(TAG, "User $userId left live room $canonicalRoom")
        } catch (e: Exception) {
            Log.w(TAG, "Error removing room participant: ${e.message}")
        }
    }

    /**
     * Real-time stream of classroom policies controlled by the Tutor.
     */
    fun observeRoomPolicy(roomCode: String): Flow<LiveRoomPolicy> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(LiveRoomPolicy())
            close()
            return@callbackFlow
        }
        val canonicalRoom = normalizeRoomCode(roomCode)
        var listener: ListenerRegistration? = null
        try {
            listener = fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Listen to room policy failed: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        @Suppress("UNCHECKED_CAST")
                        val kickedRaw = (snapshot.get("kickedUserIds") as? List<*>)
                            ?.mapNotNull { it as? String }
                            ?: emptyList()
                        val kickedUsersRaw = (snapshot.get("kickedUsers") as? Map<*, *>)
                            ?.entries
                            ?.mapNotNull { entry ->
                                val key = entry.key as? String ?: return@mapNotNull null
                                val value = entry.value as? String ?: "Removed Student"
                                key to value
                            }
                            ?.toMap()
                            ?: emptyMap()
                        trySend(
                            LiveRoomPolicy(
                                hostId = snapshot.getString("hostId") ?: "",
                                hostName = snapshot.getString("hostName") ?: "",
                                isRoomLocked = snapshot.getBoolean("isRoomLocked") ?: false,
                                allowStudentMic = snapshot.getBoolean("allowStudentMic") ?: true,
                                allowStudentCamera = snapshot.getBoolean("allowStudentCamera") ?: true,
                                allowStudentChat = snapshot.getBoolean("allowStudentChat") ?: true,
                                allowStudentWhiteboard = snapshot.getBoolean("allowStudentWhiteboard") ?: false,
                                spotlightUserId = snapshot.getString("spotlightUserId"),
                                isScreenSharing = snapshot.getBoolean("isScreenSharing") ?: false,
                                screenShareUserId = snapshot.getString("screenShareUserId"),
                                screenShareTitle = snapshot.getString("screenShareTitle") ?: "",
                                isClassEnded = snapshot.getBoolean("isClassEnded") ?: false,
                                kickedUserIds = kickedRaw,
                                kickedUsers = kickedUsersRaw,
                                updatedAt = snapshot.getLong("updatedAt") ?: System.currentTimeMillis()
                            )
                        )
                    } else {
                        trySend(LiveRoomPolicy())
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach room policy listener: ${e.message}")
        }

        awaitClose {
            listener?.remove()
        }
    }

    /**
     * Updates global room policy fields (Tutor supreme controls).
     */
    suspend fun updateRoomPolicy(roomCode: String, updates: Map<String, Any?>) {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            val payload = updates.toMutableMap()
            payload["updatedAt"] = System.currentTimeMillis()
            fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .set(payload, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Error updating room policy: ${e.message}")
        }
    }

    suspend fun setRoomLocked(roomCode: String, locked: Boolean) {
        updateRoomPolicy(roomCode, mapOf("isRoomLocked" to locked))
    }

    suspend fun setAllStudentsMicAllowed(roomCode: String, allowed: Boolean) {
        updateRoomPolicy(roomCode, mapOf("allowStudentMic" to allowed))
    }

    suspend fun setAllStudentsCameraAllowed(roomCode: String, allowed: Boolean) {
        updateRoomPolicy(roomCode, mapOf("allowStudentCamera" to allowed))
    }

    suspend fun setStudentChatAllowed(roomCode: String, allowed: Boolean) {
        updateRoomPolicy(roomCode, mapOf("allowStudentChat" to allowed))
    }

    suspend fun setStudentWhiteboardAllowed(roomCode: String, allowed: Boolean) {
        updateRoomPolicy(roomCode, mapOf("allowStudentWhiteboard" to allowed))
    }

    suspend fun setSpotlightParticipant(roomCode: String, targetUserId: String?) {
        updateRoomPolicy(roomCode, mapOf("spotlightUserId" to targetUserId))
    }

    suspend fun setScreenSharingState(
        roomCode: String,
        isSharing: Boolean,
        userId: String?,
        presentationTitle: String = ""
    ) {
        val updates = mutableMapOf<String, Any?>(
            "isScreenSharing" to isSharing,
            "screenShareUserId" to if (isSharing) userId else null,
            "screenShareTitle" to if (isSharing) presentationTitle else ""
        )
        if (isSharing && !userId.isNullOrBlank()) {
            updates["spotlightUserId"] = userId
        }
        updateRoomPolicy(roomCode, updates)
    }

    /**
     * Mutes every non-host student immediately and revokes individual speak overrides.
     */
    suspend fun muteAllStudents(roomCode: String) {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            updateRoomPolicy(canonicalRoom, mapOf("allowStudentMic" to false))
            val snapshot = fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_PARTICIPANTS)
                .get()
                .await()
            val batch = fs.batch()
            snapshot.documents.forEach { doc ->
                val isHostDoc = doc.getBoolean("isHost") ?: false
                if (!isHostDoc) {
                    batch.update(
                        doc.reference,
                        mapOf(
                            "forceMutedByTutor" to true,
                            "isMicEnabled" to false,
                            "canSpeakWhenLocked" to false
                        )
                    )
                }
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.w(TAG, "Error muting all students: ${e.message}")
        }
    }

    suspend fun muteParticipant(roomCode: String, targetUserId: String, forceMute: Boolean) {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            val updates = mutableMapOf<String, Any>(
                "forceMutedByTutor" to forceMute
            )
            if (forceMute) {
                updates["isMicEnabled"] = false
                updates["canSpeakWhenLocked"] = false
            }
            fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_PARTICIPANTS)
                .document(targetUserId)
                .set(updates, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Error muting participant: ${e.message}")
        }
    }

    suspend fun disableParticipantCamera(roomCode: String, targetUserId: String, forceOff: Boolean) {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            val updates = mutableMapOf<String, Any>(
                "forceCameraOffByTutor" to forceOff
            )
            if (forceOff) {
                updates["isCameraEnabled"] = false
            }
            fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_PARTICIPANTS)
                .document(targetUserId)
                .set(updates, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Error toggling participant camera override: ${e.message}")
        }
    }

    suspend fun grantSpeakingPermission(roomCode: String, targetUserId: String, allow: Boolean) {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            val updates = mapOf(
                "canSpeakWhenLocked" to allow,
                "forceMutedByTutor" to !allow,
                "isHandRaised" to false
            )
            fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_PARTICIPANTS)
                .document(targetUserId)
                .set(updates, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Error granting speak permission: ${e.message}")
        }
    }

    suspend fun lowerParticipantHand(roomCode: String, targetUserId: String) {
        setHandRaised(roomCode, targetUserId, false)
    }

    /**
     * Kicks a student out of the live classroom and blocks re-entry until permitted.
     */
    suspend fun kickParticipant(roomCode: String, targetUserId: String, targetName: String = "") {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            val safeName = targetName.ifBlank { "Removed Student" }
            val existingUsersMap = try {
                val snap = fs.collection(COLLECTION_LIVE_ROOMS).document(canonicalRoom).get().await()
                @Suppress("UNCHECKED_CAST")
                (snap.get("kickedUsers") as? Map<String, String>)?.toMutableMap() ?: mutableMapOf()
            } catch (_: Exception) {
                mutableMapOf()
            }
            existingUsersMap[targetUserId] = safeName

            fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .set(
                    mapOf(
                        "kickedUserIds" to FieldValue.arrayUnion(targetUserId),
                        "kickedUsers" to existingUsersMap,
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
                .await()
            fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_PARTICIPANTS)
                .document(targetUserId)
                .set(mapOf("isKicked" to true, "isMicEnabled" to false, "isCameraEnabled" to false), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Error kicking participant: ${e.message}")
        }
    }

    /**
     * Allows a previously removed student to rejoin the live classroom.
     */
    suspend fun allowParticipantRejoin(roomCode: String, targetUserId: String) {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            val roomRef = fs.collection(COLLECTION_LIVE_ROOMS).document(canonicalRoom)
            val snap = roomRef.get().await()
            @Suppress("UNCHECKED_CAST")
            val updatedUsersMap = (snap.get("kickedUsers") as? Map<String, String>)
                ?.filterKeys { it != targetUserId }
                ?: emptyMap()

            roomRef.update(
                mapOf(
                    "kickedUserIds" to FieldValue.arrayRemove(targetUserId),
                    "kickedUsers" to updatedUsersMap,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()

            try {
                roomRef.collection(SUBCOLLECTION_PARTICIPANTS)
                    .document(targetUserId)
                    .update("isKicked", false)
                    .await()
            } catch (_: Exception) {
                // Participant doc may already be deleted on leaveRoom; that is expected.
            }
            Log.i(TAG, "Allowed user $targetUserId to rejoin live room $canonicalRoom")
        } catch (e: Exception) {
            Log.w(TAG, "Error allowing participant rejoin: ${e.message}")
        }
    }

    /**
     * Ends the live class for ALL participants and marks live_classes entries as ended.
     */
    suspend fun endClassForAll(roomCode: String) {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            updateRoomPolicy(canonicalRoom, mapOf("isClassEnded" to true))
            val matching = fs.collection(COLLECTION_LIVE_CLASSES)
                .whereEqualTo("roomCode", canonicalRoom)
                .get()
                .await()
            matching.documents.forEach { doc ->
                doc.reference.update("isLive", false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error ending class: ${e.message}")
        }
    }

    /**
     * Syncs a participant's active audio/video hardware state so others see accurate indicators.
     */
    suspend fun updateParticipantMediaState(
        roomCode: String,
        userId: String,
        isMicEnabled: Boolean,
        isCameraEnabled: Boolean
    ) {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_PARTICIPANTS)
                .document(userId)
                .set(
                    mapOf(
                        "isMicEnabled" to isMicEnabled,
                        "isCameraEnabled" to isCameraEnabled
                    ),
                    SetOptions.merge()
                )
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Error syncing media state: ${e.message}")
        }
    }

    /**
     * Real-time stream of all participants inside this specific live room.
     */
    fun observeRoomParticipants(roomCode: String): Flow<List<LiveRoomParticipant>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val canonicalRoom = normalizeRoomCode(roomCode)
        var listener: ListenerRegistration? = null
        try {
            listener = fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_PARTICIPANTS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Listen to room participants failed: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val participants = snapshot.documents.mapNotNull { doc ->
                            try {
                                val roleStr = doc.getString("role") ?: "STUDENT"
                                val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.STUDENT }
                                LiveRoomParticipant(
                                    userId = doc.getString("userId") ?: doc.id,
                                    name = doc.getString("name") ?: "Participant",
                                    role = role,
                                    isHost = doc.getBoolean("isHost") ?: false,
                                    isHandRaised = doc.getBoolean("isHandRaised") ?: false,
                                    isMicEnabled = doc.getBoolean("isMicEnabled") ?: true,
                                    isCameraEnabled = doc.getBoolean("isCameraEnabled") ?: true,
                                    forceMutedByTutor = doc.getBoolean("forceMutedByTutor") ?: false,
                                    forceCameraOffByTutor = doc.getBoolean("forceCameraOffByTutor") ?: false,
                                    canSpeakWhenLocked = doc.getBoolean("canSpeakWhenLocked") ?: false,
                                    isKicked = doc.getBoolean("isKicked") ?: false,
                                    sessionToken = doc.getLong("sessionToken") ?: 0L,
                                    joinedAt = doc.getLong("joinedAt") ?: System.currentTimeMillis()
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        trySend(participants)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach participants listener: ${e.message}")
        }

        awaitClose {
            listener?.remove()
        }
    }

    /**
     * Toggles a student's hand-raise status so all participants and the tutor see it live.
     */
    suspend fun setHandRaised(roomCode: String, userId: String, isRaised: Boolean) {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_PARTICIPANTS)
                .document(userId)
                .set(mapOf("isHandRaised" to isRaised), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Error updating hand raise status: ${e.message}")
        }
    }

    // =========================================================================
    // WebRTC Mesh Signaling (SDP Offers, Answers, & ICE Candidates)
    // =========================================================================

    suspend fun sendOffer(
        roomCode: String,
        channelId: String,
        fromUid: String,
        sdp: String,
        offerTimestamp: Long,
        offerSessionToken: Long
    ) {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            val data = hashMapOf(
                "channelId" to channelId,
                "offerSdp" to sdp,
                "offerFromUid" to fromUid,
                "offerTimestamp" to offerTimestamp,
                "offerSessionToken" to offerSessionToken,
                "answerSdp" to "",
                "answerFromUid" to "",
                "answerTimestamp" to 0L,
                "answerForOfferToken" to 0L,
                "answerSessionToken" to 0L
            )
            fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_SIGNALS)
                .document(channelId)
                .set(data, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Error sending SDP offer on $channelId: ${e.message}")
        }
    }

    suspend fun sendAnswer(
        roomCode: String,
        channelId: String,
        fromUid: String,
        sdp: String,
        offerTimestamp: Long,
        answerForOfferToken: Long,
        answerSessionToken: Long
    ) {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            val data = hashMapOf(
                "answerSdp" to sdp,
                "answerFromUid" to fromUid,
                "answerTimestamp" to offerTimestamp,
                "answerForOfferToken" to answerForOfferToken,
                "answerSessionToken" to answerSessionToken
            )
            fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_SIGNALS)
                .document(channelId)
                .set(data, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Error sending SDP answer on $channelId: ${e.message}")
        }
    }

    fun observeSignalChannel(roomCode: String, channelId: String): Flow<WebRtcSignalChannel?> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val canonicalRoom = normalizeRoomCode(roomCode)
        var listener: ListenerRegistration? = null
        try {
            listener = fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_SIGNALS)
                .document(channelId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Signal channel listen error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        trySend(
                            WebRtcSignalChannel(
                                channelId = snapshot.getString("channelId") ?: channelId,
                                offerSdp = snapshot.getString("offerSdp") ?: "",
                                offerFromUid = snapshot.getString("offerFromUid") ?: "",
                                offerTimestamp = snapshot.getLong("offerTimestamp") ?: 0L,
                                offerSessionToken = snapshot.getLong("offerSessionToken") ?: 0L,
                                answerSdp = snapshot.getString("answerSdp") ?: "",
                                answerFromUid = snapshot.getString("answerFromUid") ?: "",
                                answerTimestamp = snapshot.getLong("answerTimestamp") ?: 0L,
                                answerForOfferToken = snapshot.getLong("answerForOfferToken") ?: 0L,
                                answerSessionToken = snapshot.getLong("answerSessionToken") ?: 0L
                            )
                        )
                    } else {
                        trySend(null)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to observe signal channel $channelId: ${e.message}")
        }
        awaitClose {
            listener?.remove()
        }
    }

    suspend fun sendIceCandidate(
        roomCode: String,
        channelId: String,
        senderUid: String,
        sdpMid: String,
        sdpMLineIndex: Int,
        sdp: String,
        senderSessionToken: Long
    ) {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            val data = hashMapOf(
                "senderUid" to senderUid,
                "sdpMid" to sdpMid,
                "sdpMLineIndex" to sdpMLineIndex,
                "sdp" to sdp,
                "senderSessionToken" to senderSessionToken,
                "timestamp" to System.currentTimeMillis()
            )
            fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_SIGNALS)
                .document(channelId)
                .collection(SUBCOLLECTION_ICE_CANDIDATES)
                .add(data)
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Error sending ICE candidate: ${e.message}")
        }
    }

    fun observeIceCandidates(
        roomCode: String,
        channelId: String,
        remoteUid: String
    ): Flow<List<WebRtcIceCandidatePayload>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            close()
            return@callbackFlow
        }
        val canonicalRoom = normalizeRoomCode(roomCode)
        var listener: ListenerRegistration? = null
        try {
            listener = fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_SIGNALS)
                .document(channelId)
                .collection(SUBCOLLECTION_ICE_CANDIDATES)
                .whereEqualTo("senderUid", remoteUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "ICE listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val candidates = snapshot.documents.mapNotNull { doc ->
                            WebRtcIceCandidatePayload(
                                id = doc.id,
                                senderUid = doc.getString("senderUid") ?: "",
                                sdpMid = doc.getString("sdpMid") ?: "",
                                sdpMLineIndex = (doc.getLong("sdpMLineIndex") ?: 0L).toInt(),
                                sdp = doc.getString("sdp") ?: "",
                                senderSessionToken = doc.getLong("senderSessionToken") ?: 0L,
                                timestamp = doc.getLong("timestamp") ?: 0L
                            )
                        }
                        if (candidates.isNotEmpty()) {
                            trySend(candidates)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to listen ICE candidates: ${e.message}")
        }
        awaitClose {
            listener?.remove()
        }
    }

    // =========================================================================
    // In-Class Q&A Messages, Shared Lecture Board, & Reactions
    // =========================================================================

    fun observeRoomMessages(roomCode: String): Flow<List<LiveClassMessage>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val canonicalRoom = normalizeRoomCode(roomCode)
        var listener: ListenerRegistration? = null
        try {
            listener = fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_MESSAGES)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Listen to room messages failed: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val messages = snapshot.documents.mapNotNull { doc ->
                            try {
                                val roleStr = doc.getString("senderRole") ?: "STUDENT"
                                val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.STUDENT }
                                LiveClassMessage(
                                    id = doc.getString("id") ?: doc.id,
                                    senderId = doc.getString("senderId") ?: "",
                                    senderName = doc.getString("senderName") ?: "Participant",
                                    senderRole = role,
                                    text = doc.getString("text") ?: "",
                                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                                    isQuestion = doc.getBoolean("isQuestion") ?: false
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        trySend(messages)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach messages listener: ${e.message}")
        }

        awaitClose {
            listener?.remove()
        }
    }

    suspend fun sendRoomMessage(roomCode: String, message: LiveClassMessage) {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            val docRef = if (message.id.isBlank()) {
                fs.collection(COLLECTION_LIVE_ROOMS).document(canonicalRoom).collection(SUBCOLLECTION_MESSAGES).document()
            } else {
                fs.collection(COLLECTION_LIVE_ROOMS).document(canonicalRoom).collection(SUBCOLLECTION_MESSAGES).document(message.id)
            }
            val data = hashMapOf(
                "id" to docRef.id,
                "senderId" to message.senderId,
                "senderName" to message.senderName,
                "senderRole" to message.senderRole.name,
                "text" to message.text,
                "timestamp" to message.timestamp,
                "isQuestion" to message.isQuestion
            )
            docRef.set(data).await()
        } catch (e: Exception) {
            Log.w(TAG, "Error sending room message: ${e.message}")
        }
    }

    fun observeSharedNotes(roomCode: String): Flow<String> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend("")
            close()
            return@callbackFlow
        }

        val canonicalRoom = normalizeRoomCode(roomCode)
        var listener: ListenerRegistration? = null
        try {
            listener = fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_SHARED)
                .document("notes")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Listen to shared notes failed: ${error.message}")
                        return@addSnapshotListener
                    }
                    val content = snapshot?.getString("content") ?: ""
                    trySend(content)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach notes listener: ${e.message}")
        }

        awaitClose {
            listener?.remove()
        }
    }

    suspend fun updateSharedNotes(roomCode: String, content: String) {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            val data = hashMapOf(
                "content" to content,
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_SHARED)
                .document("notes")
                .set(data, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Error updating shared notes: ${e.message}")
        }
    }

    suspend fun sendReaction(roomCode: String, emoji: String, senderName: String) {
        val fs = firestore ?: return
        val canonicalRoom = normalizeRoomCode(roomCode)
        try {
            val data = hashMapOf(
                "emoji" to emoji,
                "senderName" to senderName,
                "timestamp" to System.currentTimeMillis()
            )
            fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_REACTIONS)
                .document()
                .set(data)
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Error broadcasting reaction: ${e.message}")
        }
    }

    fun observeReactions(roomCode: String): Flow<Pair<String, String>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            close()
            return@callbackFlow
        }

        val canonicalRoom = normalizeRoomCode(roomCode)
        var listener: ListenerRegistration? = null
        val startTime = System.currentTimeMillis() - 5000L
        try {
            listener = fs.collection(COLLECTION_LIVE_ROOMS)
                .document(canonicalRoom)
                .collection(SUBCOLLECTION_REACTIONS)
                .whereGreaterThan("timestamp", startTime)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    val doc = snapshot?.documents?.firstOrNull() ?: return@addSnapshotListener
                    val emoji = doc.getString("emoji") ?: ""
                    val sender = doc.getString("senderName") ?: ""
                    if (emoji.isNotBlank()) {
                        trySend(Pair(emoji, sender))
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to listen for reactions: ${e.message}")
        }

        awaitClose {
            listener?.remove()
        }
    }
}
