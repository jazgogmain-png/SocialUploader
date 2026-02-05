package com.example.socialuploader

import android.app.Activity
import android.content.Intent
import com.tiktok.open.sdk.auth.AuthApi
import com.tiktok.open.sdk.auth.AuthRequest
import com.tiktok.open.sdk.auth.utils.PKCEUtils

class TikTokAuthManager(private val activity: Activity) {

    private val clientKey = com.example.socialuploader.BuildConfig.TIKTOK_KEY
    private val redirectUri = "https://io.github.com/jazgogmain-png/callback"

    fun login() {
        val codeVerifier = PKCEUtils.generateCodeVerifier()
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

        return if (response != null && response.isSuccess) {
            response.authCode
        } else {
            null
        }
    }
}