package com.example.socialuploader

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var authManager: TikTokAuthManager
    private var selectedVideoUri by mutableStateOf<Uri?>(null)
    private var accessToken by mutableStateOf<String?>(null)
    private var isGenerating by mutableStateOf(false)
    private var telemetryLog by mutableStateOf("SYSTEM READY")
    private var systemLog by mutableStateOf("LOGGER IDLE...")
    private var fullScript by mutableStateOf("")
    private var finalCaptionForUpload by mutableStateOf("")
    private var showSettings by mutableStateOf(false)
    private var apiKeyInput by mutableStateOf("")
    private var basePromptInput by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = TikTokAuthManager(this)
        accessToken = authManager.getSavedToken()
        apiKeyInput = authManager.getApiKeys()
        basePromptInput = authManager.getPrompts()

        handleIntent(intent)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = Color(0xFF1A1C1E), background = Color(0xFF111315))) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DashboardScreen(
                        onLoginClick = {
                            Log.d("LOLA_DEBUG", "--- PHASE 1: UI LOGIN TAPPED ---")
                            authManager.login()
                        },
                        videoUri = selectedVideoUri,
                        onVideoSelected = { uri ->
                            try {
                                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                selectedVideoUri = uri
                            } catch (e: Exception) {
                                selectedVideoUri = uri
                                Log.e("LOLA_DEBUG", "UI: URI Perm Denial: ${e.message}")
                            }
                        },
                        onGenerateMagic = { isTaglish -> generateMagic(isTaglish) },
                        onUltraMagic = { runUltraSlop() },
                        onUploadTrigger = { caption ->
                            startUploadWorker(caption)
                            resetDashboard()
                        },
                        isLoggedIn = accessToken != null,
                        isGenerating = isGenerating,
                        fullScript = fullScript,
                        finalCaption = finalCaptionForUpload,
                        onCaptionChange = { text -> finalCaptionForUpload = text },
                        onSettingsClick = { showSettings = true },
                        telemetry = telemetryLog,
                        logs = systemLog
                    )

                    if (showSettings) {
                        EngineRoom(
                            currentKeys = apiKeyInput,
                            currentPrompts = basePromptInput,
                            logs = systemLog,
                            onSave = { keys, prompts ->
                                apiKeyInput = keys
                                basePromptInput = prompts
                                authManager.saveApiKeys(keys)
                                authManager.savePrompts(prompts)
                                showSettings = false
                            },
                            onDismiss = { showSettings = false }
                        )
                    }
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
        if (intent == null) return
        val authCode = authManager.handleAuthResponse(intent)
        if (authCode != null) {
            lifecycleScope.launch {
                telemetryLog = "EXCHANGING..."
                accessToken = authManager.getAccessToken(authCode)
                if (accessToken != null) {
                    telemetryLog = "SECURED."
                    logEvent("LOLA: Handshake Success.")
                }
            }
        }
    }

    private fun logEvent(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        systemLog += "\n[$time] $message"
        Log.d("LOLA_DEBUG", message)
    }

    private fun generateMagic(isTaglish: Boolean) {
        val uri = selectedVideoUri ?: return
        val keys = apiKeyInput.split("\n").filter { it.isNotBlank() }.shuffled()
        isGenerating = true
        telemetryLog = "SCANNING..."
        lifecycleScope.launch(Dispatchers.IO) {
            val videoBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            withContext(Dispatchers.Main) {
                if (videoBytes == null) { isGenerating = false; return@withContext }
                logEvent("Capture: ${videoBytes.size / 1024} KB mapped.")
                val style = if (isTaglish) "Taglish viral mix. ✨" else "English energy. 🏁"
                for (key in keys) {
                    try {
                        val model = GenerativeModel("gemini-3-flash-preview", key.trim())
                        val response = model.generateContent(content {
                            blob("video/mp4", videoBytes)
                            text("3 viral captions. Style: $style. Exactly 5 hashtags.")
                        })
                        response.text?.let { raw ->
                            fullScript = raw
                            logEvent("GEMINI_READY")
                            telemetryLog = "SUCCESS."
                            isGenerating = false
                            return@withContext
                        }
                    } catch (e: Exception) { continue }
                }
                isGenerating = false
            }
        }
    }

    private fun startUploadWorker(caption: String) {
        logEvent("WORKER_START")
        val inputData = Data.Builder()
            .putString("TOKEN", accessToken)
            .putString("VIDEO_URI", selectedVideoUri.toString())
            .putString("CAPTION", caption)
            .build()

        val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>().setInputData(inputData).build()
        WorkManager.getInstance(this).enqueue(uploadRequest)
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(uploadRequest.id).observe(this) { info ->
            if (info != null) logEvent("Worker: ${info.state}")
        }
    }

    private fun runUltraSlop() { /* ... unchanged ... */ }
    private fun resetDashboard() { selectedVideoUri = null; finalCaptionForUpload = "" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onLoginClick: () -> Unit,
    videoUri: Uri?,
    onVideoSelected: (Uri) -> Unit,
    onGenerateMagic: (Boolean) -> Unit,
    onUltraMagic: () -> Unit,
    onUploadTrigger: (String) -> Unit,
    isLoggedIn: Boolean,
    isGenerating: Boolean,
    fullScript: String,
    finalCaption: String,
    onCaptionChange: (String) -> Unit,
    onSettingsClick: () -> Unit,
    telemetry: String,
    logs: String
) {
    val scrollState = rememberScrollState()
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onVideoSelected(uri)
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("@DriftingGrandma", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, null, tint = Color.Gray) }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(4.dp)).padding(8.dp)) {
            Column {
                Text(text = "> $telemetry", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Green)
                Box(modifier = Modifier.height(80.dp).verticalScroll(rememberScrollState())) {
                    Text(text = logs, color = Color.Green.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
            Text(if (videoUri != null) "✅ VIDEO LOADED" else "SELECT VIDEO")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onGenerateMagic(true) }, modifier = Modifier.weight(1f)) { Text("TAGLISH") }
            Button(onClick = { onGenerateMagic(false) }, modifier = Modifier.weight(1f)) { Text("ENGLISH") }
        }

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(value = finalCaption, onValueChange = onCaptionChange, modifier = Modifier.fillMaxWidth().height(150.dp), label = { Text("Output") })

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { onUploadTrigger(finalCaption) }, enabled = isLoggedIn, modifier = Modifier.fillMaxWidth().height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))) {
            Text("PUSH TO DRAFTS", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onLoginClick, modifier = Modifier.fillMaxWidth()) {
            Text(if (isLoggedIn) "REFRESH TIKTOK" else "CONNECT TIKTOK", color = Color.White)
        }
    }
}