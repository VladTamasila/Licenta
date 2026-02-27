package com.example.mental_healt_chatbot

data class ChatRequest(
    val message: String
)

data class ChatResponse(
    val reply: String
)
