package com.example.mental_healt_chatbot

import androidx.compose.runtime.mutableStateOf

// stare globala de lock - cand iese din app marcheaza ca trebuie reintrodus PIN-ul
// observatorul de lifecycle din MainActivity seteaza needsUnlock = true la ON_STOP
object AppLockState {
    val needsUnlock = mutableStateOf(false)

    fun lock()   { needsUnlock.value = true }
    fun unlock() { needsUnlock.value = false }
}
