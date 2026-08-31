package com.example.mental_healt_chatbot

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// gestioneaza PIN-ul local: stocheaza DOAR hash + salt, nu PIN-ul in clar
// asa daca cineva extrage prefs-ul, tot nu poate recupera PIN-ul
class PinManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_pin",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ─── PIN ──────────────────────────────────────────────────────────────────

    fun isPinSet(): Boolean {
        return prefs.getString(KEY_HASH, null) != null
    }

    fun setPin(pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        prefs.edit()
            .putString(KEY_HASH, b64(hash))
            .putString(KEY_SALT, b64(salt))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val savedHash = prefs.getString(KEY_HASH, null) ?: return false
        val savedSalt = prefs.getString(KEY_SALT, null) ?: return false
        val computed = pbkdf2(pin, fromB64(savedSalt))
        // comparare in timp constant ca sa nu fie atac de tip timing
        return constantTimeEquals(b64(computed), savedHash)
    }

    fun clearPin() {
        prefs.edit()
            .remove(KEY_HASH)
            .remove(KEY_SALT)
            .apply()
    }

    // ─── BIOMETRIC ────────────────────────────────────────────────────────────

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIO, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIO, enabled).apply()
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun b64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun fromB64(s: String): ByteArray =
        Base64.decode(s, Base64.NO_WRAP)

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    companion object {
        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_BIO  = "pin_bio_enabled"

        private const val SALT_BYTES = 16
        private const val ITERATIONS = 120_000
        private const val KEY_BITS   = 256
    }
}
