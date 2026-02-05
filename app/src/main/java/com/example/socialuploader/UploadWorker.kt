package com.example.socialuploader

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class UploadWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val videoPath = inputData.getString("VIDEO_PATH") ?: return Result.failure()
        val caption = inputData.getString("CAPTION") ?: ""

        Log.d("GrandmaPoster", "Starting background upload for: $videoPath")

        return try {
            // TODO: In the next step, we'll add the TikTok Retrofit/Ktor calls here.
            // For now, it just simulates a successful background trigger.

            Log.d("GrandmaPoster", "Upload successful for: $caption")
            Result.success()
        } catch (e: Exception) {
            Log.e("GrandmaPoster", "Upload failed", e)
            Result.retry()
        }
    }
}