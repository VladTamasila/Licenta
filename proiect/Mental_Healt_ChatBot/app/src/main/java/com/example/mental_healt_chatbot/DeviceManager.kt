package com.example.mental_healt_chatbot

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

// genereaza si pastreaza un identificator stabil pentru device:
// - in primul rand ANDROID_ID (supravietuieste reinstall-ului aceleiasi aplicatii)
// - daca nu il poate citi, genereaza un UUID local salvat in EncryptedSharedPreferences
// id-ul e folosit ca header X-Device-Id pentru ca backend-ul sa stie pe ce device este userul
class DeviceManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_device",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val ctx = context.applicationContext

    @SuppressLint("HardwareIds")
    fun deviceId(): String {
        // 1. cautam un id deja salvat
        val cached = prefs.getString(KEY_ID, null)
        if (!cached.isNullOrBlank()) return cached

        // 2. ANDROID_ID - 64-bit hex unic per device per app-signing key
        val androidId = try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) { null }

        val id = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            "aid_$androidId"
        } else {
            // 3. fallback - generam UUID o singura data si il salvam
            "uuid_" + UUID.randomUUID().toString()
        }

        prefs.edit().putString(KEY_ID, id).apply()
        return id
    }

    companion object {
        private const val KEY_ID = "device_id"
    }
}
