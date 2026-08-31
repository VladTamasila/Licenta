package com.example.mental_healt_chatbot

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Base64
import org.json.JSONObject

class SessionManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        prefs.edit().putString("jwt", token).apply()
    }

    fun getToken(): String? = prefs.getString("jwt", null)

    fun clear() {
        prefs.edit().remove("jwt").apply()
    }

    // verifică exp din JWT (standard: payload.exp în secunde)
    fun isTokenValid(): Boolean {
        val token = getToken() ?: return false
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return false
            val payloadJson = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP))
            val payload = JSONObject(payloadJson)
            val expSec = payload.optLong("exp", 0L)
            val nowSec = System.currentTimeMillis() / 1000
            expSec > nowSec + 5 // mic buffer
        } catch (e: Exception) {
            false
        }
    }
}
