package com.example.mental_healt_chatbot

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricHelper {

    // verifica daca telefonul are senzor activ si configurat
    fun canUseBiometric(activity: FragmentActivity): Boolean {
        val mgr = BiometricManager.from(activity)
        val res = mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return res == BiometricManager.BIOMETRIC_SUCCESS
    }

    // arata prompt-ul de amprenta/fata; daca e ok cheama onSuccess, altfel onFail
    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFail: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFail()
            }
            // failed o ignoram - userul poate sa mai incerce, e gestionat de prompt
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Deblochează MindBuddy")
            .setSubtitle("Folosește amprenta sau recunoașterea facială")
            .setNegativeButtonText("Folosește PIN-ul")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(info)
    }
}
