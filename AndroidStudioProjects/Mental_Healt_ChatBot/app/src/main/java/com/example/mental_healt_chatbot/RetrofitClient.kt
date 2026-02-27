package com.example.mental_healt_chatbot

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import android.content.Context
object RetrofitClient {

    private const val BASE_URL = "https://nonfeebly-unpalisaded-zackary.ngrok-free.dev"
    private const val APP_SECRET = "MentalHealthBot"
    private var retrofit: Retrofit? = null

    fun api(context: Context): ApiService {
        if (retrofit == null) {
            val session = SessionManager(context)

            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val builder = chain.request().newBuilder()
                        .addHeader("X-App-Secret", APP_SECRET)

                    val token = session.getToken()
                    if (!token.isNullOrBlank()) {
                        builder.addHeader("Authorization", "Bearer $token")
                    }
                    chain.proceed(builder.build())
                }
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(ApiService::class.java)
    }
}
