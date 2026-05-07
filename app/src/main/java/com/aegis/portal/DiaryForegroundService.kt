package com.aegis.portal

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class DiaryForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "diary_fg"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, DiaryForegroundService::class.java)
            context.startForegroundService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID,
            "AEGIS 알림 수집",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "백그라운드에서 알림을 수집합니다"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AEGIS")
            .setContentText("알림 수집 중")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(tapIntent)
            .build()
    }
}
