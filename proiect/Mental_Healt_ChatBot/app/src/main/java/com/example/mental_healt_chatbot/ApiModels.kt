package com.example.mental_healt_chatbot

// ─── CHAT ─────────────────────────────────────────────────────────────────────

data class SendMessageRequest(
    val message: String
)

data class ChatResponse(
    val reply: String,
    val emotion: EmotionDto? = null,
    val safety: SafetyStatusDto? = null
)

data class EmotionDto(
    val happy: Int,
    val sad: Int,
    val anxious: Int,
    val angry: Int,
    val neutral: Int,
    val dominant_emotion: String
)

// mod privat - istoricul vine din memorie, nu din DB
data class PrivateChatMessage(
    val role: String,    // "user" sau "assistant"
    val content: String
)

data class PrivateChatRequest(
    val message: String,
    val history: List<PrivateChatMessage>
)

data class PrivateChatResponse(
    val reply: String
)

// ─── CONVERSAȚII ──────────────────────────────────────────────────────────────

data class ConversationDto(
    val id: String,
    val title: String,
    val created_at: String,
    val updated_at: String? = null
)

data class MessageDto(
    val id: String,
    val role: String,
    val content: String,
    val created_at: String
)

// ─── MOOD ─────────────────────────────────────────────────────────────────────

data class MoodEntryDto(
    val created_at: String,
    val happy: Int,
    val sad: Int,
    val anxious: Int,
    val angry: Int,
    val neutral: Int
)

// ─── SAFETY ───────────────────────────────────────────────────────────────────

data class SafetyStatusDto(
    val state: String,                  // ok | concerning | crisis
    val forced_hourly: Boolean = false,
    val locked_at: String? = null,
    val has_trusted_contact: Boolean? = null,
    val trusted_contact_email: String? = null
)

data class DeviceCheckRequest(val device_id: String)

data class DeviceCheckResponse(
    val device_locked: Boolean,
    val locked_at: String? = null
)

data class TrustedContactRequest(val email: String)

data class Phq2Request(val q1: Int, val q2: Int)
