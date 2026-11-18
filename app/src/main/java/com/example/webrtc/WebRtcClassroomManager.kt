package com.example.webrtc

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.repository.LiveClassRepository
import com.example.model.LiveRoomParticipant
import com.example.model.WebRtcIceCandidatePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Production native two-way WebRTC Audio + Video Engine backed by Firestore signaling.
 * Owns camera & microphone capture cleanly so there is zero camera contention,
 * renders HD self-view and all remote participant video tracks, and streams bidirectional audio.
 */
class WebRtcClassroomManager(
    context: Context,
    private val roomCode: String,
    private val localUserId: String,
    private val repo: LiveClassRepository,
    private val localSessionToken: Long = System.currentTimeMillis()
) {
    companion object {
        private const val TAG = "WebRtcClassroom"
        @Volatile
        private var isFactoryInitialized = false

        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
        )
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    val eglBase: EglBase = EglBase.create()
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null

    private var localVideoTrackInternal: VideoTrack? = null
    private var localAudioTrackInternal: AudioTrack? = null

    // Screen sharing state & resources
    private var screenSurfaceTextureHelper: SurfaceTextureHelper? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var screenVideoSource: VideoSource? = null
    private var screenVideoTrackInternal: VideoTrack? = null

    private val _isScreenSharing = MutableStateFlow(false)
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTracks = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    val remoteVideoTracks: StateFlow<Map<String, VideoTrack>> = _remoteVideoTracks.asStateFlow()

    private val _peerConnectionStates = MutableStateFlow<Map<String, String>>(emptyMap())
    val peerConnectionStates: StateFlow<Map<String, String>> = _peerConnectionStates.asStateFlow()

    private var isCameraCapturing = false
    private var cameraDesiredEnabled = true
    @Volatile
    private var isDisposed = false

    private class PeerSession(
        val remoteUid: String,
        val channelId: String,
        val amIOfferer: Boolean,
        val remoteSessionToken: Long
    ) {
        var peerConnection: PeerConnection? = null
        var channelJob: Job? = null
        var iceJob: Job? = null
        var iceRestartCount: Int = 0
        val processedIceIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val pendingRemoteIce: MutableList<IceCandidate> = mutableListOf()
        @Volatile
        var hasRemoteDescription: Boolean = false
        var lastHandledOfferKey: String = ""
        var lastHandledAnswerKey: String = ""
        var currentOfferTimestamp: Long = 0L
        var offerSeq: Long = 0L
    }

    private val peerSessions = ConcurrentHashMap<String, PeerSession>()

    init {
        initializeFactory()
    }

    private fun initializeFactory() {
        try {
            if (!isFactoryInitialized) {
                synchronized(WebRtcClassroomManager::class.java) {
                    if (!isFactoryInitialized) {
                        val initOptions = PeerConnectionFactory.InitializationOptions.builder(appContext)
                            .setEnableInternalTracer(false)
                            .createInitializationOptions()
                        PeerConnectionFactory.initialize(initOptions)
                        isFactoryInitialized = true
                    }
                }
            }

            val adm = JavaAudioDeviceModule.builder(appContext)
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setUseHardwareAcousticEchoCanceler(JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
                .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
                .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                    override fun onWebRtcAudioRecordInitError(errorMessage: String?) {
                        Log.w(TAG, "AudioRecord init error: $errorMessage")
                    }
                    override fun onWebRtcAudioRecordStartError(
                        errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?,
                        errorMessage: String?
                    ) {
                        Log.w(TAG, "AudioRecord start error: $errorCode $errorMessage")
                    }
                    override fun onWebRtcAudioRecordError(errorMessage: String?) {
                        Log.w(TAG, "AudioRecord error: $errorMessage")
                    }
                })
                .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                    override fun onWebRtcAudioTrackInitError(errorMessage: String?) {
                        Log.w(TAG, "AudioTrack init error: $errorMessage")
                    }
                    override fun onWebRtcAudioTrackStartError(
                        errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode?,
                        errorMessage: String?
                    ) {
                        Log.w(TAG, "AudioTrack start error: $errorCode $errorMessage")
                    }
                    override fun onWebRtcAudioTrackError(errorMessage: String?) {
                        Log.w(TAG, "AudioTrack error: $errorMessage")
                    }
                })
                .createAudioDeviceModule()
            adm.setSpeakerMute(false)
            audioDeviceModule = adm

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
                .createPeerConnectionFactory()
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing PeerConnectionFactory: ${t.message}", t)
        }
    }

    /**
     * Initializes local camera + microphone WebRTC tracks once runtime permissions are granted.
     */
    fun startLocalMedia(startCamera: Boolean, startMic: Boolean) {
        if (isDisposed) return
        val factory = peerConnectionFactory ?: return

        val hasAudioPerm = androidx.core.content.ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCameraPerm = androidx.core.content.ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        var attachedNewTrack = false

        if (hasAudioPerm && localAudioTrackInternal == null) {
            try {
                val audioConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                }
                val aSource = factory.createAudioSource(audioConstraints)
                audioSource = aSource
                val aTrack = factory.createAudioTrack("slaty_audio_$localUserId", aSource).apply {
                    setEnabled(startMic)
                }
                localAudioTrackInternal = aTrack
                audioDeviceModule?.setMicrophoneMute(!startMic)
                audioDeviceModule?.setSpeakerMute(false)
                attachedNewTrack = true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start local audio track: ${t.message}", t)
            }
        }

        if (hasCameraPerm && localVideoTrackInternal == null) {
            try {
                val capturer = createCameraCapturer()
                if (capturer != null) {
                    videoCapturer = capturer
                    val helper = SurfaceTextureHelper.create("SlatyCaptureThread", eglBase.eglBaseContext)
                    surfaceTextureHelper = helper
                    val vSource = factory.createVideoSource(capturer.isScreencast)
                    videoSource = vSource
                    capturer.initialize(helper, appContext, vSource.capturerObserver)
                    if (startCamera) {
                        try {
                            capturer.startCapture(640, 480, 30)
                            isCameraCapturing = true
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed initial camera capture start: ${e.message}")
                        }
                    }
                    val vTrack = factory.createVideoTrack("slaty_video_$localUserId", vSource).apply {
                        setEnabled(startCamera)
                    }
                    localVideoTrackInternal = vTrack
                    _localVideoTrack.value = vTrack
                    attachedNewTrack = true
                } else {
                    Log.w(TAG, "No camera device found on this device")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start local video capturer: ${t.message}", t)
            }
        }

        if (attachedNewTrack) {
            peerSessions.values.forEach { session ->
                val pc = session.peerConnection ?: return@forEach
                attachLocalTracksToPeerConnection(pc, createRecvOnlyIfMissing = false)
                if (session.amIOfferer && pc.signalingState() == PeerConnection.SignalingState.STABLE) {
                    scope.launch {
                        sendFreshOffer(session, iceRestart = false)
                    }
                }
            }
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator: CameraEnumerator = try {
            if (Camera2Enumerator.isSupported(appContext)) {
                Camera2Enumerator(appContext)
            } else {
                Camera1Enumerator(false)
            }
        } catch (t: Throwable) {
            Camera1Enumerator(false)
        }

        val deviceNames = try { enumerator.deviceNames } catch (_: Throwable) { emptyArray() }
        for (name in deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                val capturer = enumerator.createCapturer(name, null)
                if (capturer != null) return capturer
            }
        }
        for (name in deviceNames) {
            val capturer = enumerator.createCapturer(name, null)
            if (capturer != null) return capturer
        }
        return null
    }

    fun setLocalCameraEnabled(enabled: Boolean) {
        if (isDisposed) return
        cameraDesiredEnabled = enabled
        if (_isScreenSharing.value) {
            return
        }
        try {
            localVideoTrackInternal?.setEnabled(enabled)
            val capturer = videoCapturer ?: return
            if (enabled && !isCameraCapturing) {
                capturer.startCapture(640, 480, 30)
                isCameraCapturing = true
            } else if (!enabled && isCameraCapturing) {
                capturer.stopCapture()
                isCameraCapturing = false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error toggling camera capture: ${t.message}")
        }
    }

    /**
     * Starts live device screen sharing (documents, PDFs, slides, apps) using Android MediaProjection
     * and swaps the outbound video stream for all connected students.
     */
    fun startScreenShare(
        mediaProjectionPermissionResultData: Intent,
        onStoppedBySystem: () -> Unit
    ): Boolean {
        if (isDisposed) return false
        val factory = peerConnectionFactory ?: return false
        if (_isScreenSharing.value) stopScreenShare()

        return try {
            if (isCameraCapturing) {
                try {
                    videoCapturer?.stopCapture()
                    isCameraCapturing = false
                } catch (_: Throwable) {}
            }

            val capturer = ScreenCapturerAndroid(
                mediaProjectionPermissionResultData,
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        scope.launch {
                            stopScreenShare()
                            onStoppedBySystem()
                        }
                    }
                }
            )
            val helper = SurfaceTextureHelper.create("SlatyScreenThread", eglBase.eglBaseContext)
            val source = factory.createVideoSource(capturer.isScreencast)
            capturer.initialize(helper, appContext, source.capturerObserver)
            capturer.startCapture(1280, 720, 15)

            val track = factory.createVideoTrack("slaty_screen_$localUserId", source).apply {
                setEnabled(true)
            }

            screenCapturer = capturer
            screenSurfaceTextureHelper = helper
            screenVideoSource = source
            screenVideoTrackInternal = track

            _isScreenSharing.value = true
            _localVideoTrack.value = track
            replaceOutboundVideoTrack(track)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start screen share: ${t.message}", t)
            stopScreenShare()
            false
        }
    }

    /**
     * Stops active screen sharing and smoothly restores the tutor's live camera stream.
     */
    fun stopScreenShare() {
        val wasSharing = _isScreenSharing.value
        _isScreenSharing.value = false

        try {
            screenCapturer?.stopCapture()
        } catch (_: Throwable) {}
        try {
            screenCapturer?.dispose()
        } catch (_: Throwable) {}
        try {
            screenVideoTrackInternal?.setEnabled(false)
            screenVideoTrackInternal?.dispose()
        } catch (_: Throwable) {}
        try {
            screenVideoSource?.dispose()
        } catch (_: Throwable) {}
        try {
            screenSurfaceTextureHelper?.dispose()
        } catch (_: Throwable) {}

        screenCapturer = null
        screenVideoTrackInternal = null
        screenVideoSource = null
        screenSurfaceTextureHelper = null

        if (!isDisposed) {
            val camTrack = localVideoTrackInternal
            if (cameraDesiredEnabled && !isCameraCapturing) {
                try {
                    videoCapturer?.startCapture(640, 480, 30)
                    isCameraCapturing = true
                    camTrack?.setEnabled(true)
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed resuming camera after screen share: ${t.message}")
                }
            }
            if (wasSharing) {
                _localVideoTrack.value = camTrack
                if (camTrack != null) {
                    replaceOutboundVideoTrack(camTrack)
                }
            }
        }
    }

    private fun replaceOutboundVideoTrack(newTrack: VideoTrack) {
        peerSessions.values.forEach { session ->
            val pc = session.peerConnection ?: return@forEach
            try {
                val videoTransceiver = pc.transceivers.firstOrNull {
                    it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO
                }
                val videoSender = videoTransceiver?.sender ?: pc.senders.firstOrNull {
                    it.track()?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND
                }
                if (videoSender != null) {
                    videoSender.setTrack(newTrack, false)
                    val wasRecvOnly = videoTransceiver?.direction == RtpTransceiver.RtpTransceiverDirection.RECV_ONLY
                    if (videoTransceiver != null && videoTransceiver.direction != RtpTransceiver.RtpTransceiverDirection.SEND_RECV) {
                        videoTransceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                    }
                    if (wasRecvOnly && session.amIOfferer && pc.signalingState() == PeerConnection.SignalingState.STABLE) {
                        scope.launch { sendFreshOffer(session, iceRestart = false) }
                    }
                } else {
                    pc.addTrack(newTrack, listOf("slaty_stream_$localUserId"))
                    if (session.amIOfferer && pc.signalingState() == PeerConnection.SignalingState.STABLE) {
                        scope.launch { sendFreshOffer(session, iceRestart = false) }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error swapping outbound video track for ${session.remoteUid}: ${t.message}")
            }
        }
    }

    fun setLocalMicEnabled(enabled: Boolean) {
        if (isDisposed) return
        try {
            localAudioTrackInternal?.setEnabled(enabled)
            audioDeviceModule?.setMicrophoneMute(!enabled)
        } catch (t: Throwable) {
            Log.w(TAG, "Error toggling microphone: ${t.message}")
        }
    }

    fun switchCamera() {
        if (isDisposed || _isScreenSharing.value) return
        try {
            videoCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                    Log.i(TAG, "Switched camera, front=$isFrontCamera")
                }

                override fun onCameraSwitchError(errorDescription: String?) {
                    Log.w(TAG, "Camera switch error: $errorDescription")
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Could not switch camera: ${t.message}")
        }
    }

    /**
     * Synchronizes mesh PeerConnections against live Firestore participant roster.
     */
    fun syncParticipants(participants: List<LiveRoomParticipant>) {
        if (isDisposed) return
        val remotePeers = participants.filter { it.userId != localUserId && !it.isKicked }
        val activeIds = remotePeers.map { it.userId }.toSet()

        val removedIds = peerSessions.keys.filter { it !in activeIds }
        removedIds.forEach { uid ->
            closePeerSession(uid)
        }

        remotePeers.forEach { remote ->
            val existing = peerSessions[remote.userId]
            if (existing == null) {
                startPeerSession(remote)
            } else if (remote.sessionToken != 0L && remote.sessionToken != existing.remoteSessionToken) {
                closePeerSession(remote.userId)
                startPeerSession(remote)
            }
        }
    }

    private fun startPeerSession(remote: LiveRoomParticipant) {
        val factory = peerConnectionFactory ?: return
        val remoteUid = remote.userId
        val channelId = LiveClassRepository.buildChannelId(localUserId, remoteUid)
        val amIOfferer = localUserId < remoteUid

        val session = PeerSession(
            remoteUid = remoteUid,
            channelId = channelId,
            amIOfferer = amIOfferer,
            remoteSessionToken = remote.sessionToken
        )
        peerSessions[remoteUid] = session
        _peerConnectionStates.update { it + (remoteUid to "Connecting...") }

        val pc = createPeerConnectionForSession(factory, session)
        session.peerConnection = pc
        if (pc == null) {
            _peerConnectionStates.update { it + (remoteUid to "Connection unavailable") }
            return
        }

        // Listen for remote ICE candidates matched by remote sessionToken (clock-skew immune)
        session.iceJob = scope.launch {
            repo.observeIceCandidates(roomCode, channelId, remoteUid).collect { candidates ->
                candidates.forEach { payload ->
                    if (session.remoteSessionToken != 0L &&
                        payload.senderSessionToken != 0L &&
                        payload.senderSessionToken != session.remoteSessionToken
                    ) {
                        return@forEach
                    }
                    if (session.processedIceIds.add(payload.id)) {
                        handleRemoteIceCandidate(session, payload)
                    }
                }
            }
        }

        if (amIOfferer) {
            scope.launch {
                sendFreshOffer(session, iceRestart = false)
            }

            session.channelJob = scope.launch {
                repo.observeSignalChannel(roomCode, channelId).collect { signal ->
                    if (signal == null || isDisposed) return@collect
                    if (signal.answerSdp.isBlank() || signal.answerFromUid != remoteUid) return@collect

                    // Only accept answers intended for our current session & offer timestamp
                    if (signal.answerForOfferToken != 0L && signal.answerForOfferToken != localSessionToken) {
                        return@collect
                    }
                    if (session.remoteSessionToken != 0L &&
                        signal.answerSessionToken != 0L &&
                        signal.answerSessionToken != session.remoteSessionToken
                    ) {
                        return@collect
                    }
                    if (signal.answerTimestamp != session.currentOfferTimestamp) {
                        return@collect
                    }

                    val answerKey = "${signal.answerSessionToken}_${signal.answerTimestamp}"
                    if (answerKey == session.lastHandledAnswerKey) return@collect
                    session.lastHandledAnswerKey = answerKey

                    val currentPc = session.peerConnection ?: return@collect
                    val ok = setRemoteDescriptionSdp(
                        currentPc,
                        SessionDescription(SessionDescription.Type.ANSWER, signal.answerSdp)
                    )
                    if (ok) {
                        session.hasRemoteDescription = true
                        drainPendingIce(session)
                    }
                }
            }
        } else {
            session.channelJob = scope.launch {
                repo.observeSignalChannel(roomCode, channelId).collect { signal ->
                    if (signal == null || isDisposed) return@collect
                    if (signal.offerSdp.isBlank() || signal.offerFromUid != remoteUid) return@collect

                    // Reject stale offers left behind from older disconnected sessions
                    if (session.remoteSessionToken != 0L &&
                        signal.offerSessionToken != 0L &&
                        signal.offerSessionToken != session.remoteSessionToken
                    ) {
                        return@collect
                    }

                    val offerKey = "${signal.offerSessionToken}_${signal.offerTimestamp}"
                    if (offerKey == session.lastHandledOfferKey) return@collect
                    session.lastHandledOfferKey = offerKey

                    var currentPc = session.peerConnection
                    val needsRebuild = currentPc == null ||
                        currentPc.signalingState() != PeerConnection.SignalingState.STABLE

                    if (needsRebuild) {
                        try {
                            currentPc?.close()
                            currentPc?.dispose()
                        } catch (_: Throwable) {}
                        session.hasRemoteDescription = false
                        synchronized(session.pendingRemoteIce) {
                            session.pendingRemoteIce.clear()
                        }
                        currentPc = createPeerConnectionForSession(factory, session)
                        session.peerConnection = currentPc
                    }
                    if (currentPc == null) return@collect

                    val remoteSet = setRemoteDescriptionSdp(
                        currentPc,
                        SessionDescription(SessionDescription.Type.OFFER, signal.offerSdp)
                    )
                    if (remoteSet) {
                        session.hasRemoteDescription = true
                        drainPendingIce(session)
                        val answerSdp = createAnswerSdp(currentPc)
                        if (answerSdp != null) {
                            val localSet = setLocalDescriptionSdp(
                                currentPc,
                                SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                            )
                            if (localSet) {
                                repo.sendAnswer(
                                    roomCode = roomCode,
                                    channelId = channelId,
                                    fromUid = localUserId,
                                    sdp = answerSdp,
                                    offerTimestamp = signal.offerTimestamp,
                                    answerForOfferToken = signal.offerSessionToken,
                                    answerSessionToken = localSessionToken
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun sendFreshOffer(session: PeerSession, iceRestart: Boolean) {
        val pc = session.peerConnection ?: return
        if (isDisposed) return
        session.offerSeq += 1L
        val offerTs = maxOf(System.currentTimeMillis(), session.currentOfferTimestamp + 1L)
        session.currentOfferTimestamp = offerTs
        val offerSdp = createOfferSdp(pc, iceRestart = iceRestart) ?: return
        val localSet = setLocalDescriptionSdp(pc, SessionDescription(SessionDescription.Type.OFFER, offerSdp))
        if (localSet) {
            repo.sendOffer(
                roomCode = roomCode,
                channelId = session.channelId,
                fromUid = localUserId,
                sdp = offerSdp,
                offerTimestamp = offerTs,
                offerSessionToken = localSessionToken
            )
        }
    }

    private fun createPeerConnectionForSession(
        factory: PeerConnectionFactory,
        session: PeerSession
    ): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            iceCandidatePoolSize = 4
        }

        val pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                val label = when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        session.iceRestartCount = 0
                        "Live Audio & Video"
                    }
                    PeerConnection.IceConnectionState.CHECKING -> "Connecting..."
                    PeerConnection.IceConnectionState.DISCONNECTED -> "Reconnecting..."
                    PeerConnection.IceConnectionState.FAILED -> "Network retrying..."
                    PeerConnection.IceConnectionState.CLOSED -> "Disconnected"
                    else -> "Connecting..."
                }
                _peerConnectionStates.update { it + (session.remoteUid to label) }

                if (newState == PeerConnection.IceConnectionState.FAILED &&
                    session.amIOfferer &&
                    session.iceRestartCount < 2 &&
                    !isDisposed &&
                    peerSessions.containsKey(session.remoteUid)
                ) {
                    session.iceRestartCount++
                    scope.launch {
                        sendFreshOffer(session, iceRestart = true)
                    }
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null || isDisposed) return
                scope.launch {
                    repo.sendIceCandidate(
                        roomCode = roomCode,
                        channelId = session.channelId,
                        senderUid = localUserId,
                        sdpMid = candidate.sdpMid ?: "",
                        sdpMLineIndex = candidate.sdpMLineIndex,
                        sdp = candidate.sdp ?: "",
                        senderSessionToken = localSessionToken
                    )
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                if (stream == null) return
                stream.audioTracks.forEach { it.setEnabled(true) }
                val videoTrack = stream.videoTracks.firstOrNull()
                if (videoTrack != null) {
                    videoTrack.setEnabled(true)
                    _remoteVideoTracks.update { it + (session.remoteUid to videoTrack) }
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {
                _remoteVideoTracks.update { it - session.remoteUid }
            }

            override fun onDataChannel(dataChannel: DataChannel?) {}

            override fun onRenegotiationNeeded() {
                val currentPc = session.peerConnection ?: return
                if (!isDisposed &&
                    session.amIOfferer &&
                    session.hasRemoteDescription &&
                    currentPc.signalingState() == PeerConnection.SignalingState.STABLE
                ) {
                    scope.launch {
                        sendFreshOffer(session, iceRestart = false)
                    }
                }
            }

            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                handleRemoteTrack(session.remoteUid, receiver?.track())
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                handleRemoteTrack(session.remoteUid, transceiver?.receiver?.track())
            }
        })

        attachLocalTracksToPeerConnection(pc, createRecvOnlyIfMissing = true)
        return pc
    }

    private fun handleRemoteTrack(remoteUid: String, track: MediaStreamTrack?) {
        when (track) {
            is VideoTrack -> {
                track.setEnabled(true)
                _remoteVideoTracks.update { it + (remoteUid to track) }
            }
            is AudioTrack -> {
                track.setEnabled(true)
            }
        }
    }

    private fun attachLocalTracksToPeerConnection(
        pc: PeerConnection?,
        createRecvOnlyIfMissing: Boolean
    ) {
        if (pc == null) return
        val streamId = "slaty_stream_$localUserId"
        try {
            val transceivers = pc.transceivers
            val audioTransceiver = transceivers.firstOrNull {
                it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO
            }
            val videoTransceiver = transceivers.firstOrNull {
                it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO
            }

            val aTrack = localAudioTrackInternal
            if (aTrack != null) {
                if (audioTransceiver == null) {
                    pc.addTrack(aTrack, listOf(streamId))
                } else {
                    if (audioTransceiver.sender.track() == null) {
                        audioTransceiver.sender.setTrack(aTrack, false)
                    }
                    audioTransceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                }
            } else if (audioTransceiver == null && createRecvOnlyIfMissing) {
                pc.addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
                )
            }

            val vTrack = if (_isScreenSharing.value) {
                screenVideoTrackInternal ?: localVideoTrackInternal
            } else {
                localVideoTrackInternal
            }
            if (vTrack != null) {
                if (videoTransceiver == null) {
                    pc.addTrack(vTrack, listOf(streamId))
                } else {
                    if (videoTransceiver.sender.track() == null) {
                        videoTransceiver.sender.setTrack(vTrack, false)
                    }
                    videoTransceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                }
            } else if (videoTransceiver == null && createRecvOnlyIfMissing) {
                pc.addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error attaching local tracks: ${t.message}")
        }
    }

    private fun handleRemoteIceCandidate(session: PeerSession, payload: WebRtcIceCandidatePayload) {
        val candidate = IceCandidate(payload.sdpMid, payload.sdpMLineIndex, payload.sdp)
        val pc = session.peerConnection
        if (session.hasRemoteDescription && pc != null) {
            pc.addIceCandidate(candidate)
        } else {
            synchronized(session.pendingRemoteIce) {
                session.pendingRemoteIce.add(candidate)
            }
        }
    }

    private fun drainPendingIce(session: PeerSession) {
        val pc = session.peerConnection ?: return
        val copy = synchronized(session.pendingRemoteIce) {
            val list = session.pendingRemoteIce.toList()
            session.pendingRemoteIce.clear()
            list
        }
        copy.forEach { pc.addIceCandidate(it) }
    }

    private suspend fun createOfferSdp(pc: PeerConnection, iceRestart: Boolean = false): String? =
        suspendCancellableCoroutine { cont ->
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                if (iceRestart) {
                    mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
                }
            }
            pc.createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (cont.isActive) cont.resume(desc?.description)
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) {
                    Log.w(TAG, "createOffer failed: $error")
                    if (cont.isActive) cont.resume(null)
                }
                override fun onSetFailure(error: String?) {}
            }, constraints)
        }

    private suspend fun createAnswerSdp(pc: PeerConnection): String? =
        suspendCancellableCoroutine { cont ->
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
            pc.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (cont.isActive) cont.resume(desc?.description)
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) {
                    Log.w(TAG, "createAnswer failed: $error")
                    if (cont.isActive) cont.resume(null)
                }
                override fun onSetFailure(error: String?) {}
            }, constraints)
        }

    private suspend fun setLocalDescriptionSdp(pc: PeerConnection, desc: SessionDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            pc.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(d: SessionDescription?) {}
                override fun onSetSuccess() {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onCreateFailure(error: String?) {}
                override fun onSetFailure(error: String?) {
                    Log.w(TAG, "setLocalDescription failed: $error")
                    if (cont.isActive) cont.resume(false)
                }
            }, desc)
        }

    private suspend fun setRemoteDescriptionSdp(pc: PeerConnection, desc: SessionDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            pc.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(d: SessionDescription?) {}
                override fun onSetSuccess() {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onCreateFailure(error: String?) {}
                override fun onSetFailure(error: String?) {
                    Log.w(TAG, "setRemoteDescription failed: $error")
                    if (cont.isActive) cont.resume(false)
                }
            }, desc)
        }

    private fun closePeerSession(remoteUid: String) {
        val session = peerSessions.remove(remoteUid) ?: return
        session.channelJob?.cancel()
        session.iceJob?.cancel()
        try {
            session.peerConnection?.close()
            session.peerConnection?.dispose()
        } catch (_: Throwable) {}
        _remoteVideoTracks.update { it - remoteUid }
        _peerConnectionStates.update { it - remoteUid }
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        stopScreenShare()
        scope.cancel()
        peerSessions.keys.toList().forEach { closePeerSession(it) }
        try {
            if (isCameraCapturing) {
                videoCapturer?.stopCapture()
                isCameraCapturing = false
            }
            videoCapturer?.dispose()
        } catch (_: Throwable) {}
        try {
            localVideoTrackInternal?.setEnabled(false)
            localVideoTrackInternal?.dispose()
            localAudioTrackInternal?.setEnabled(false)
            localAudioTrackInternal?.dispose()
            videoSource?.dispose()
            audioSource?.dispose()
            surfaceTextureHelper?.dispose()
            audioDeviceModule?.release()
            peerConnectionFactory?.dispose()
            eglBase.release()
        } catch (_: Throwable) {}
        _localVideoTrack.value = null
        _remoteVideoTracks.value = emptyMap()
        _peerConnectionStates.value = emptyMap()
    }
}

/**
 * Jetpack Compose wrapper around WebRTC SurfaceViewRenderer with safe onRelease lifecycle cleanup,
 * mirroring control, Z-order support for floating PiP windows, and aspect-fit document scaling.
 */
@Composable
fun WebRtcVideoView(
    videoTrack: VideoTrack?,
    eglBaseContext: EglBase.Context,
    mirror: Boolean,
    isOverlay: Boolean = false,
    scaleFit: Boolean = false,
    modifier: Modifier = Modifier
) {
    var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                id = R.id.webrtc_surface_view
                init(eglBaseContext, null)
                setEnableHardwareScaler(false)
                setScalingType(
                    if (scaleFit) RendererCommon.ScalingType.SCALE_ASPECT_FIT
                    else RendererCommon.ScalingType.SCALE_ASPECT_FILL
                )
                setZOrderMediaOverlay(isOverlay)
                setMirror(mirror)
                setTag(R.id.webrtc_surface_view, Pair(mirror, scaleFit))
                rendererRef = this
            }
        },
        update = { view ->
            @Suppress("UNCHECKED_CAST")
            val prev = view.getTag(R.id.webrtc_surface_view) as? Pair<Boolean, Boolean>
            if (prev?.first != mirror) {
                view.setMirror(mirror)
            }
            if (prev?.second != scaleFit) {
                view.setScalingType(
                    if (scaleFit) RendererCommon.ScalingType.SCALE_ASPECT_FIT
                    else RendererCommon.ScalingType.SCALE_ASPECT_FILL
                )
            }
            if (prev?.first != mirror || prev.second != scaleFit) {
                view.setTag(R.id.webrtc_surface_view, Pair(mirror, scaleFit))
            }
        },
        onRelease = { view ->
            try {
                if (rendererRef === view) rendererRef = null
                view.clearImage()
                view.release()
            } catch (_: Throwable) {}
        },
        modifier = modifier.clipToBounds()
    )

    DisposableEffect(videoTrack, rendererRef) {
        val view = rendererRef
        if (view != null && videoTrack != null) {
            try {
                videoTrack.addSink(view)
            } catch (t: Throwable) {
                Log.w("WebRtcVideoView", "Could not attach sink: ${t.message}")
            }
        }
        onDispose {
            if (view != null && videoTrack != null) {
                try {
                    videoTrack.removeSink(view)
                } catch (_: Throwable) {}
            }
        }
    }
}
