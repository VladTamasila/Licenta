package com.example.mental_healt_chatbot

data class LoginResponse(val token: String)

data class LoginRequest(
    val identifier: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

data class ForgotPasswordRequest(
    val email: String
)

data class ResendVerificationRequest(
    val email: String
)

// reset PIN: server doar valideaza identitatea (cod + parola), PIN-ul e doar local
data class PinResetVerifyRequest(
    val code: String,
    val password: String
)
