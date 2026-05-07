package com.aegis.portal

import android.content.Context
import androidx.work.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        private const val DIARY_URL = "https://diary.tkdgns.com/api/notifications"
        private const val DIARY_PIN = "3578"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "diary_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun doWork(): Result {
        val bufferFile = NotificationService.bufferFile(applicationContext)
        if (!bufferFile.exists() || bufferFile.length() == 0L) return Result.success()

        val lines: List<String>
        synchronized(bufferFile) {
            lines = bufferFile.readLines().filter { it.isNotBlank() }
            bufferFile.writeText("")
        }
        if (lines.isEmpty()) return Result.success()

        val array = JSONArray()
        lines.forEach { line ->
            runCatching { array.put(JSONObject(line)) }
        }

        return try {
            val body = array.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url(DIARY_URL)
                .addHeader("X-Diary-Pin", DIARY_PIN)
                .post(body)
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                Result.success()
            } else {
                // 실패 시 버퍼에 되돌리기
                synchronized(bufferFile) { bufferFile.appendText(lines.joinToString("\n") + "\n") }
                Result.retry()
            }
        } catch (e: Exception) {
            synchronized(bufferFile) { bufferFile.appendText(lines.joinToString("\n") + "\n") }
            Result.retry()
        }
    }
}
