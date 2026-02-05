package com.example.socialuploader

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var tiktokAuthManager: TikTokAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tiktokAuthManager = TikTokAuthManager(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PosterDashboard(
                        onLoginClick = { tiktokAuthManager.login() },
                        onGoClicked = { videoPath, caption, delayMinutes ->
                            scheduleUpload(videoPath, caption, delayMinutes)
                        }
                    )
                }
            }
        }
    }

    // This catches the "Auth Code" from TikTok when it redirects back to the app
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val authCode = tiktokAuthManager.handleAuthResponse(intent)
        if (authCode != null) {
            Toast.makeText(this, "Logged into TikTok! Code: $authCode", Toast.LENGTH_LONG).show()
            // TODO: Save this code to get your permanent Access Token
        }
    }

    private fun scheduleUpload(videoPath: String, caption: String, delay: Long) {
        val uploadWork = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .setInputData(workDataOf(
                "VIDEO_PATH" to videoPath,
                "CAPTION" to caption
            ))
            .build()

        WorkManager.getInstance(this).enqueue(uploadWork)
        Toast.makeText(this, "Drift Grandma scheduled in $delay mins", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun PosterDashboard(onLoginClick: () -> Unit, onGoClicked: (String, String, Long) -> Unit) {
    var videoPath by remember { mutableStateOf("/sdcard/Movies/grandma_drift.mp4") }
    var caption by remember { mutableStateOf("Grandma's burning rubber! #drift #jdm") }
    var delayMinutes by remember { mutableStateOf("0") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Drifting Grandma Poster", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // LOGIN BUTTON
        Button(onClick = onLoginClick, modifier = Modifier.fillMaxWidth()) {
            Text("1. LOGIN TO TIKTOK")
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        // UPLOAD FIELDS
        OutlinedTextField(value = videoPath, onValueChange = { videoPath = it }, label = { Text("Video Path") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = caption, onValueChange = { caption = it }, label = { Text("Caption") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = delayMinutes, onValueChange = { delayMinutes = it }, label = { Text("Delay (Minutes)") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { onGoClicked(videoPath, caption, delayMinutes.toLongOrNull() ?: 0L) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("2. SLAP & GO")
        }
    }
}