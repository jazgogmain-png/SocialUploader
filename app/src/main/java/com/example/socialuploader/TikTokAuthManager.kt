package com.example.socialuploader

import android.app.Activity
import android.content.Intent
import com.tiktok.open.sdk.auth.AuthApi
import com.tiktok.open.sdk.auth.AuthRequest
import com.tiktok.open.sdk.auth.utils.PKCEUtils
import io.ktor.client.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TikTokAuthManager(private val activity: Activity) {

    private val clientKey = BuildConfig.TIKTOK_KEY
    private val clientSecret = BuildConfig.TIKTOK_SECRET // Pulled from your local.properties
    private val redirectUri = "https://io.github.com/jazgogmain-png/callback"
    private val client = HttpClient()

    companion object {
        var lastCodeVerifier: String? = null
    }

    fun login() {
        val codeVerifier = PKCEUtils.generateCodeVerifier()
        lastCodeVerifier = codeVerifier

        val request = AuthRequest(
            clientKey = clientKey,
            scope = "user.info.basic,video.upload,video.publish",
            redirectUri = redirectUri,
            codeVerifier = codeVerifier
        )

        val authApi = AuthApi(activity)
        authApi.authorize(request)
    }

    fun handleAuthResponse(intent: Intent): String? {
        val authApi = AuthApi(activity)
        val response = authApi.getAuthResponseFromIntent(intent, redirectUri)
        return if (response != null && response.isSuccess) response.authCode else null
    }

    /**
     * The Exchange: Swaps the temporary authCode for a long-lived Access Token.
     */
    suspend fun getAccessToken(authCode: String): String? = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = client.submitForm(
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

            if (response.status == HttpStatusCode.OK) {
                // We'll parse the full JSON in the next step, for now just log it
                response.bodyAsText()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}