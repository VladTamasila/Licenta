package com.example.mental_healt_chatbot

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // ─── AUTH ─────────────────────────────────────────────────────────────────

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("auth/register")
    suspend fun register(@Body req: RegisterRequest): Any

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Any

    @POST("auth/resend-verification")
    suspend fun resendVerification(@Body body: ResendVerificationRequest): Any

    // ─── PIN RESET ────────────────────────────────────────────────────────────

    // cere cod pe email-ul contului logat
    @POST("auth/pin-reset/send-code")
    suspend fun requestPinResetCode(): Any

    // verifica cod + parola contului; daca ok, app-ul sterge PIN-ul local
    @POST("auth/pin-reset/verify")
    suspend fun verifyPinResetCode(@Body body: PinResetVerifyRequest): Any

    // ─── CONVERSAȚII ──────────────────────────────────────────────────────────

    @POST("conversations/new")
    suspend fun createConversation(): ConversationDto

    @GET("conversations/list")
    suspend fun getConversations(): List<ConversationDto>

    @GET("conversations/{id}/messages")
    suspend fun getMessages(@Path("id") conversationId: String): List<MessageDto>

    @POST("conversations/{id}/send-message")
    suspend fun sendMessage(
        @Path("id") conversationId: String,
        @Body body: SendMessageRequest
    ): ChatResponse

    @DELETE("conversations/{id}")
    suspend fun deleteConversation(@Path("id") conversationId: String): Any

    // ─── MOD PRIVAT ───────────────────────────────────────────────────────────

    // istoricul vine din memorie cu fiecare request - nimic nu se salveaza in DB
    @POST("chat/private")
    suspend fun sendPrivateMessage(
        @Body body: PrivateChatRequest
    ): PrivateChatResponse

    // ─── MOOD ─────────────────────────────────────────────────────────────────

    @GET("mood/entries")
    suspend fun getMoodEntries(
        @Query("from") fromIso: String,
        @Query("to") toIso: String
    ): List<MoodEntryDto>

    // ─── SAFETY ───────────────────────────────────────────────────────────────

    @GET("safety/status")
    suspend fun getSafetyStatus(): SafetyStatusDto

    @POST("safety/device-check")
    suspend fun deviceCheck(@Body body: DeviceCheckRequest): DeviceCheckResponse

    @POST("safety/set-trusted-contact")
    suspend fun setTrustedContact(@Body body: TrustedContactRequest): Any

    @POST("safety/crisis-unlock/request")
    suspend fun requestCrisisUnlock(): Any

    @POST("safety/crisis-unlock/phq2")
    suspend fun submitPhq2(@Body body: Phq2Request): Any
}