package com.example.socialuploader

import android.app.Activity
import android.content.Intent
import com.tiktok.open.sdk.auth.AuthApi
import com.tiktok.open.sdk.auth.AuthRequest

class TikTokAuthManager(private val activity: Activity) {

    // You will get this Key from the TikTok Developer Portal
    private val clientKey = "YOUR_TIKTOK_CLIENT_KEY_HERE"

    fun login() {
        val request = AuthRequest(
            clientKey = clientKey,
            // These scopes are what allow us to actually post videos
            scope = "user.info.basic,video.upload,video.publish",
            // This MUST match the Redirect URI in the TikTok Portal
            redirectUri = "driftinggrandma://callback"
        )

        val authApi = AuthApi(activity)
        authApi.authorize(request)
    }

    // This is called when TikTok sends the result back to your app
    fun handleAuthResponse(intent: Intent): String? {
        val authApi = AuthApi(activity)
        val response = authApi.getAuthResponse(intent)

        return if (response != null && response.isSuccess) {
            // This code is what we exchange for a "Post Token"
            response.authCode
        } else {
            null
        }
    }
}