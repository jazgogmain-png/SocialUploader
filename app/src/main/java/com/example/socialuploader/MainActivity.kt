package com.example.socialuploader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// These are the imports that usually cause the red mess if missing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    private lateinit var authManager: TikTokAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager = TikTokAuthManager(this)

        // Catch the auth code if the app was opened via the redirect link
        handleIntent(intent)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen(
                        onLoginClick = { authManager.login() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            val authCode = authManager.handleAuthResponse(it)
            if (authCode != null) {
                Toast.makeText(this, "Login Success! Code: $authCode", Toast.LENGTH_LONG).show()
                // The next step will be the token exchange using TikTokAuthManager.lastCodeVerifier
            }
        }
    }
}

@Composable
fun DashboardScreen(onLoginClick: () -> Unit) {
    // State to hold the selected video URI
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }

    // Launcher for the Android Photo Picker
    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🏎️ Drifting Grandma Dashboard",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Step 1: Login
        Button(
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("1. LOGIN TO TIKTOK")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Step 2: Pick Video
        Button(
            onClick = {
                pickVideoLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("2. SELECT DRIFT VIDEO")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // UI Feedback for video selection
        if (selectedVideoUri != null) {
            Text(
                text = "✅ Video Ready to Drift!",
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "File: ${selectedVideoUri.toString().takeLast(30)}",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                text = "No video selected",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}