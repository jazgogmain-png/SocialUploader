package com.example.socialuploader

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.json.JSONObject

class UploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val token = inputData.getString("TOKEN")?.trim() ?: return Result.failure()
        val videoUriString = inputData.getString("VIDEO_URI") ?: return Result.failure()
        val videoUri = Uri.parse(videoUriString)
        val caption = inputData.getString("CAPTION") ?: "Drifting Grandma"

        return try {
            val client = HttpClient()
            val fileSize = getFileSize(videoUri)
            Log.d("LOLA_DEBUG", "WORKER_START: Initializing with size $fileSize")

            // STEP 1: INITIALIZE (V2 Inbox/Drafts)
            val initResponse = client.post("https://open.tiktokapis.com/v2/post/publish/inbox/video/init/") {
                header("Authorization", "Bearer $token")
                header("Content-Type", "application/json; charset=UTF-8")
                setBody(JSONObject().apply {
                    put("post_info", JSONObject().apply {
                        put("title", caption.take(100))
                    })
                    put("source_info", JSONObject().apply {
                        put("source", "FILE_UPLOAD")
                        put("video_size", fileSize)
                        put("chunk_size", fileSize)
                        put("total_chunk_count", 1)
                    })
                }.toString())
            }

            val initBody = initResponse.bodyAsText()
            Log.d("LOLA_DEBUG", "WORKER_INIT_STATUS: ${initResponse.status}")
            Log.d("LOLA_DEBUG", "WORKER_INIT_BODY: $initBody")

            if (initResponse.status == HttpStatusCode.OK) {
                // STEP 2: PUSH BYTES
                val uploadUrl = JSONObject(initBody).getJSONObject("data").getString("upload_url")
                val videoBytes = applicationContext.contentResolver.openInputStream(videoUri)?.use { it.readBytes() }

                if (videoBytes != null) {
                    val pushResponse = client.put(uploadUrl) {
                        header("Content-Range", "bytes 0-${videoBytes.size - 1}/${videoBytes.size}")
                        header("Content-Length", videoBytes.size.toString())
                        header("Content-Type", "video/mp4")
                        setBody(videoBytes)
                    }
                    Log.d("LOLA_DEBUG", "WORKER_PUSH_STATUS: ${pushResponse.status}")
                    Result.success()
                } else {
                    Log.e("LOLA_DEBUG", "WORKER_FAIL: Bytes null")
                    Result.failure()
                }
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e("LOLA_DEBUG", "WORKER_CRITICAL: ${e.message}")
            Result.failure()
        }
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            applicationContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        } catch (e: Exception) { 0L }
    }
}