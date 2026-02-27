package com.example.mental_healt_chatbot

data class RegisterRequest(val username: String, val password: String)
data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val token: String)
