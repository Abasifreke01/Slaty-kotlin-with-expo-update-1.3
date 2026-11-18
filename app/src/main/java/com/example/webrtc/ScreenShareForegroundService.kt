package com.example.webrtc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CompletableDeferred

/**
 * Foreground service required on Android 10–16 for MediaProjection screen capture.
 * Promotes itself to foreground synchronously on start so WebRTC ScreenCapturerAndroid
 * can safely obtain the MediaProjection session without SecurityException.
 */
class ScreenShareForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "slaty_screen_share_channel"
        private const val NOTIFICATION_ID = 4102
        const val ACTION_START = "com.example.webrtc.action.START_SCREEN_SHARE"
        const val ACTION_STOP = "com.example.webrtc.action.STOP_SCREEN_SHARE"
        const val EXTRA_CLASS_TITLE = "extra_class_title"

        @Volatile
        private var startDeferred: CompletableDeferred<Unit>? = null

        suspend fun startAndAwaitReady(context: Context, classTitle: String) {
            val deferred = CompletableDeferred<Unit>()
            startDeferred = deferred
            val intent = Intent(context, ScreenShareForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CLASS_TITLE, classTitle)
            }
            ContextCompat.startForegroundService(context, intent)
            deferred.await()
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, ScreenShareForegroundService::class.java))
            } catch (_: Throwable) {}
            startDeferred?.complete(Unit)
            startDeferred = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        val classTitle = intent?.getStringExtra(EXTRA_CLASS_TITLE) ?: "Slaty Live Class"
        createNotificationChannel()
        val notification = buildNotification(classTitle)

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        } finally {
            startDeferred?.complete(Unit)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForegroundCompat()
        startDeferred?.complete(Unit)
        startDeferred = null
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Screen Sharing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while a tutor is sharing their screen or study materials live"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(classTitle: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_slaty_icon)
            .setContentTitle("Presenting Live in $classTitle")
            .setContentText("Your screen is visible to students in this Slaty classroom")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
