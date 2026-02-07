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
import androidx.compose.foundation.shape.CircleShape
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

class MainActivity : ComponentActivity() {

    private lateinit var authManager: TikTokAuthManager
    private var selectedVideoUri by mutableStateOf<Uri?>(null)
    private var accessToken by mutableStateOf<String?>(null)
    private var isGenerating by mutableStateOf(false)
    private var telemetryLog by mutableStateOf("SYSTEM READY")
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
                        onLoginClick = { authManager.login() },
                        videoUri = selectedVideoUri,
                        onVideoSelected = { selectedVideoUri = it },
                        onGenerateMagic = { isTaglish -> generateMagic(isTaglish) },
                        onUltraMagic = { runUltraSlop() }, // This now maps correctly
                        onUploadTrigger = { startUploadWorker(it); resetDashboard() },
                        isLoggedIn = accessToken != null,
                        isGenerating = isGenerating,
                        fullScript = fullScript,
                        finalCaption = finalCaptionForUpload,
                        onCaptionChange = { finalCaptionForUpload = it },
                        onSettingsClick = { showSettings = true },
                        telemetry = telemetryLog
                    )

                    if (showSettings) {
                        EngineRoom(
                            currentKeys = apiKeyInput,
                            currentPrompts = basePromptInput,
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

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val authCode = authManager.handleAuthResponse(intent)
        if (authCode != null) {
            lifecycleScope.launch {
                telemetryLog = "EXCHANGING CODE..."
                val token = authManager.getAccessToken(authCode)
                if (token != null) {
                    accessToken = token
                    telemetryLog = "TIKTOK LIVE."
                }
            }
        }
    }

    private fun generateMagic(isTaglish: Boolean) {
        val uri = selectedVideoUri ?: return
        val keys = apiKeyInput.split("\n").filter { it.isNotBlank() }
        isGenerating = true
        telemetryLog = "G3 PIXEL SCANNING..."

        lifecycleScope.launch(Dispatchers.IO) {
            val videoBytes = try {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) {
                Log.e("LOLA_DEBUG", "Read Error: ${e.message}")
                null
            }

            withContext(Dispatchers.Main) {
                if (videoBytes == null) {
                    telemetryLog = "ERROR: BYTES EMPTY."
                    isGenerating = false
                    return@withContext
                }

                val promptText = if (isTaglish) {
                    "3 LONG High-Tier Taglish/Bisaya viral captions. Mix English and Filipino naturally. Slang: 'lods', 'kol', 'shabay-shabay'. Exactly 5 hashtags."
                } else {
                    "3 LONG English drift captions. Car slang. Exactly 5 hashtags."
                }

                for (key in keys) {
                    try {
                        val model = GenerativeModel("gemini-3-flash-preview", key.trim())
                        val response = model.generateContent(
                            content {
                                blob("video/mp4", videoBytes)
                                text("Watch the pixels. $promptText \n STRICT FORMAT: \n CAPTION 1: \n CAPTION 2: \n CAPTION 3: \n HASHTAGS: \n MUSIC: \n OVERLAY:")
                            }
                        )

                        response.text?.let { raw ->
                            Log.d("LOLA_DEBUG", "RAW RESPONSE: $raw")
                            fullScript = raw
                            telemetryLog = "SCAN SUCCESS."
                        }
                        isGenerating = false
                        return@withContext
                    } catch (e: Exception) {
                        continue
                    }
                }
                telemetryLog = "G3 ERROR."
                isGenerating = false
            }
        }
    }

    private fun runUltraSlop() {
        val keys = apiKeyInput.split("\n").filter { it.isNotBlank() }
        if (selectedVideoUri == null || keys.isEmpty()) return
        isGenerating = true
        lifecycleScope.launch {
            for (key in keys) {
                try {
                    val model = GenerativeModel("gemini-3-flash-preview", key.trim())
                    val response = model.generateContent(basePromptInput)
                    response.text?.let {
                        val caption = it.replace("**", "").replace("*", "").take(150)
                        startUploadWorker(caption)
                        resetDashboard()
                    }
                    isGenerating = false
                    return@launch
                } catch (e: Exception) { continue }
            }
            isGenerating = false
        }
    }

    private fun resetDashboard() {
        selectedVideoUri = null
        finalCaptionForUpload = ""
    }

    private fun startUploadWorker(caption: String) {
        val inputData = Data.Builder()
            .putString("TOKEN", accessToken)
            .putString("VIDEO_URI", selectedVideoUri.toString())
            .putString("CAPTION", caption)
            .build()
        WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<UploadWorker>().setInputData(inputData).build())
    }
} // End MainActivity

// --- UI COMPONENTS (OUTSIDE) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onLoginClick: () -> Unit,
    videoUri: Uri?,
    onVideoSelected: (Uri) -> Unit,
    onGenerateMagic: (Boolean) -> Unit,
    onUltraMagic: () -> Unit, // Fixed name here to match MainActivity
    onUploadTrigger: (String) -> Unit,
    isLoggedIn: Boolean,
    isGenerating: Boolean,
    fullScript: String,
    finalCaption: String,
    onCaptionChange: (String) -> Unit,
    onSettingsClick: () -> Unit,
    telemetry: String
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) onVideoSelected(it) }
    var lastTapTime by remember { mutableStateOf(0L) }

    fun copyToClipboard(text: String, label: String) {
        if (text.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label Copied!", Toast.LENGTH_SHORT).show()
    }

    fun scrub(text: String) = text.replace("**", "").replace("*", "").replace("\"", "").trim()

    fun extract(script: String, prefix: String): String {
        val regex = Regex("(?i)\\*?\\*?$prefix:?\\*?\\*?\\s*(.*?)(?=\\n[A-Z\\s0-9]+:|$)", RegexOption.DOT_MATCHES_ALL)
        return regex.find(script)?.groupValues?.get(1)?.trim() ?: ""
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("@DriftingGrandma", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, null, tint = Color.Gray) }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(4.dp)).padding(8.dp)) {
            Text(text = "> $telemetry", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Green)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Card(onClick = { pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }, modifier = Modifier.fillMaxWidth().height(80.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { Text(if (videoUri != null) "✅ VIDEO LOADED" else "TAP TO SELECT VIDEO") }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onGenerateMagic(true) }, enabled = videoUri != null && !isGenerating, modifier = Modifier.weight(1f).height(55.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))) {
                if (isGenerating) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("TAGLISH")
            }
            Button(onClick = { onGenerateMagic(false) }, enabled = videoUri != null && !isGenerating, modifier = Modifier.weight(1f).height(55.dp)) {
                if (isGenerating) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("ENGLISH")
            }
        }

        if (fullScript.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            val hashtags = scrub(extract(fullScript, "HASHTAGS"))

            listOf("CAPTION 1", "CAPTION 2", "CAPTION 3").forEach { tag ->
                val cap = scrub(extract(fullScript, tag))
                if (cap.isNotEmpty()) {
                    Button(onClick = { onCaptionChange("$cap\n\n$hashtags") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))) {
                        Text("SELECT $tag + TAGS")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { copyToClipboard(scrub(extract(fullScript, "OVERLAY")), "Overlay") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))) { Text("OVERLAY") }
                Button(onClick = { copyToClipboard(scrub(extract(fullScript, "MUSIC")), "Music") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))) { Text("MUSIC") }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(value = finalCaption, onValueChange = onCaptionChange, modifier = Modifier.fillMaxWidth().height(150.dp), label = { Text("Final Output") })

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { if (isLoggedIn) onUploadTrigger(finalCaption) else onLoginClick() }, modifier = Modifier.fillMaxWidth().height(65.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isLoggedIn) Color(0xFFFE2C55) else Color.Gray)) {
            Text(if (isLoggedIn) "PUSH TO DRAFTS 🇵🇭" else "CONNECT TIKTOK", fontWeight = FontWeight.ExtraBold)
        }

        Spacer(modifier = Modifier.height(40.dp))
        Box(modifier = Modifier.align(Alignment.CenterHorizontally).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).clickable {
            val current = System.currentTimeMillis()
            if (current - lastTapTime < 500) onUltraMagic() else Toast.makeText(context, "Double-tap for Slop", Toast.LENGTH_SHORT).show()
            lastTapTime = current
        }.padding(8.dp)) { Text("u-slop", fontSize = 10.sp, color = Color.DarkGray) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineRoom(currentKeys: String, currentPrompts: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var keys by remember { mutableStateOf(currentKeys) }
    var prompts by remember { mutableStateOf(currentPrompts) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1A1C1E)) {
        Column(modifier = Modifier.padding(24.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text("⚙️ ENGINE ROOM", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(value = keys, onValueChange = { keys = it }, modifier = Modifier.fillMaxWidth().height(100.dp), label = { Text("API KEYS") })
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = prompts, onValueChange = { prompts = it }, modifier = Modifier.fillMaxWidth().height(150.dp), label = { Text("FACTORY PROMPT") })
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { onSave(keys, prompts) }, modifier = Modifier.fillMaxWidth().height(55.dp)) { Text("SAVE") }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}