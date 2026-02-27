package com.example.mental_healt_chatbot

import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("chat")
    suspend fun sendMessage(@Body request: ChatRequest): ChatResponse

    @POST("auth/register")
    suspend fun register(@Body req: RegisterRequest): Any

    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): LoginResponse

}
