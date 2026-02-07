package com.example.socialuploader

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.json.JSONObject
import java.io.File

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val client = HttpClient(Android)

    override suspend fun doWork(): Result {
        val accessToken = inputData.getString("TOKEN") ?: return Result.failure()
        val videoUri = Uri.parse(inputData.getString("VIDEO_URI"))
        val caption = inputData.getString("CAPTION") ?: ""

        return try {
            val videoFile = getFileFromUri(videoUri) ?: return Result.failure()
            val fileSize = videoFile.length()

            // Step 1: Initialize
            val initRes = client.post("https://open.tiktokapis.com/v2/post/publish/video/init/") {
                header("Authorization", "Bearer $accessToken")
                header("Content-Type", "application/json; charset=UTF-8")
                setBody("""
                    {
                        "post_info": { "title": "$caption", "privacy_level": "SELF_ONLY" },
                        "source_info": { "source": "FILE_UPLOAD", "video_size": $fileSize, "chunk_size": $fileSize, "total_chunk_count": 1 }
                    }
                """.trimIndent())
            }

            val initData = JSONObject(initRes.bodyAsText()).getJSONObject("data")
            val uploadUrl = initData.getString("upload_url")

            // Step 2: Push Bytes
            client.put(uploadUrl) {
                header("Content-Range", "bytes 0-${fileSize - 1}/$fileSize")
                header("Content-Type", "video/mp4")
                setBody(videoFile.readBytes())
            }

            Log.e("LOLA_DEBUG", "DRIFT SUCCESSFUL: Video is in Drafts!")
            Result.success()
        } catch (e: Exception) {
            Log.e("LOLA_DEBUG", "UPLOAD FAILED: ${e.message}")
            Result.failure()
        }
    }

    private fun getFileFromUri(uri: Uri): File? {
        val inputStream = applicationContext.contentResolver.openInputStream(uri)
        val tempFile = File(applicationContext.cacheDir, "upload.mp4")
        inputStream?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
        return tempFile
    }
}