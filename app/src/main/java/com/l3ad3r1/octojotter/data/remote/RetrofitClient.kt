package com.l3ad3r1.octojotter.data.remote

import com.l3ad3r1.octojotter.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://api.github.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /**
     * Request logging, debug builds only.
     *
     * `Level.BODY` logs request headers and bodies — which here means the
     * `Authorization: Bearer <personal access token>` header and the full text
     * of every synced note, written to logcat. That is fine on a development
     * machine and is not something a shipped build should ever do, so release
     * builds get `Level.NONE`. The Authorization header is redacted even in
     * debug: nothing about diagnosing a sync problem requires seeing the token,
     * and logs get pasted into bug reports.
     */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        redactHeader("Authorization")
        redactHeader("Cookie")
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val githubApiService: GithubApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GithubApiService::class.java)
    }
}
