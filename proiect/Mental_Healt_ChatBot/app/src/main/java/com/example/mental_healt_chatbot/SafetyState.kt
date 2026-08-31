package com.example.mental_healt_chatbot

import androidx.compose.runtime.mutableStateOf

// stare globala de safety - actualizata din raspunsurile backend-ului
// hasTrustedContact e Boolean? - null cat timp nu stim sigur, false doar
// cand backend-ul ne-a raspuns explicit cu "fara contact". Asa nu mai apare
// banner-ul fals-pozitiv cand request-ul esueaza si default-ul ar fi false.
object SafetyState {
    val state             = mutableStateOf("ok")        // ok | concerning | crisis
    val forcedHourly      = mutableStateOf(false)
    val lockedAt          = mutableStateOf<String?>(null)
    val hasTrustedContact = mutableStateOf<Boolean?>(null)
    val trustedContactEmail = mutableStateOf<String?>(null)

    fun apply(s: SafetyStatusDto) {
        state.value = s.state
        forcedHourly.value = s.forced_hourly
        lockedAt.value = s.locked_at
        hasTrustedContact.value = s.has_trusted_contact
        trustedContactEmail.value = s.trusted_contact_email
    }
}
