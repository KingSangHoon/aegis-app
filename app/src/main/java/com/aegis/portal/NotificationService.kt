package com.aegis.portal

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.io.File
import java.time.Instant

class NotificationService : NotificationListenerService() {

    companion object {
        private val SYSTEM_PREFIXES = listOf(
            "android",
            "com.android.",
            "com.samsung.",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.sec.",
            "com.qualcomm.",
            "com.mediatek.",
        )

        fun bufferFile(context: android.content.Context): File =
            File(context.filesDir, "notif_buffer.jsonl")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return

        if (SYSTEM_PREFIXES.any { pkg == it || pkg.startsWith(it) }) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text  = extras.getCharSequence("android.text")?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return

        val appName = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        }.getOrDefault(pkg)

        val obj = JSONObject().apply {
            put("app", appName)
            put("package_name", pkg)
            put("title", title)
            put("body", text)
            put("received_at", Instant.now().toString().replace("Z", "+00:00").substring(0, 19) + "+00:00")
        }

        synchronized(this) {
            bufferFile(this).appendText(obj.toString() + "\n")
        }
    }
}
