package com.example.socialuploader

import android.app.Activity
import android.content.Intent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.tiktok.open.sdk.auth.AuthApi
import com.tiktok.open.sdk.auth.AuthRequest
import com.tiktok.open.sdk.auth.utils.PKCEUtils
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class TikTokAuthManager(private val activity: Activity) {

    private val clientKey = BuildConfig.TIKTOK_KEY
    private val clientSecret = BuildConfig.TIKTOK_SECRET
    private val redirectUri = "https://io.github.com/jazgogmain-png/callback"
    private val client = HttpClient(Android)

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val prefs = EncryptedSharedPreferences.create(
        "lola_vault", masterKeyAlias, activity,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKeys(keys: String) = prefs.edit().putString("gemini_pool", keys).apply()
    fun getApiKeys(): String = prefs.getString("gemini_pool", "") ?: ""

    fun savePrompts(prompts: String) = prefs.edit().putString("prompt_library", prompts).apply()
    fun getPrompts(): String = prefs.getString("prompt_library", "Provide 2 viral captions for drifting grandma...") ?: ""

    fun saveToken(token: String) = prefs.edit().putString("access_token", token).apply()
    fun getSavedToken(): String? = prefs.getString("access_token", null)

    companion object { var lastCodeVerifier: String? = null }

    fun login() {
        val codeVerifier = PKCEUtils.generateCodeVerifier()
        lastCodeVerifier = codeVerifier
        AuthApi(activity).authorize(AuthRequest(clientKey, "user.info.basic,video.upload,video.publish", redirectUri, codeVerifier))
    }

    fun handleAuthResponse(intent: Intent): String? {
        val response = AuthApi(activity).getAuthResponseFromIntent(intent, redirectUri)
        return if (response != null && response.isSuccess) response.authCode else null
    }

    suspend fun getAccessToken(authCode: String): String? = withContext(Dispatchers.IO) {
        try {
            val res: HttpResponse = client.submitForm(
                url = "https://open.tiktokapis.com/v2/oauth/token/",
                formParameters = parameters {
                    append("client_key", clientKey)
                    append("client_secret", clientSecret)
                    append("code", authCode)
                    append("grant_type", "authorization_code")
                    append("redirect_uri", redirectUri)
                    append("code_verifier", lastCodeVerifier ?: "")
                }
            )
            if (res.status == HttpStatusCode.OK) {
                val token = JSONObject(res.bodyAsText()).optString("access_token")
                saveToken(token)
                token
            } else null
        } catch (e: Exception) { null }
    }
}