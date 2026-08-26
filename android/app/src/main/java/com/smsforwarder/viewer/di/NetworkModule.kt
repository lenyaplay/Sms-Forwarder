package com.smsforwarder.viewer.di

import android.content.Context
import com.smsforwarder.viewer.data.local.SessionEvents
import com.smsforwarder.viewer.data.local.TokenStore
import com.smsforwarder.viewer.data.remote.ApiService
import com.smsforwarder.viewer.data.remote.AuthAuthenticator
import com.smsforwarder.viewer.data.remote.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Overridable in tests (e.g. pointed at a MockWebServer instance).
    // 10.0.2.2 is the emulator's alias for the host machine's 127.0.0.1 - a
    // physical device instead needs `adb reverse tcp:8080 tcp:8080` (or a
    // real configurable server URL - see Milestone 9 backlog).
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/"

    @Provides
    @Singleton
    @Named("baseUrl")
    fun provideBaseUrl(): String = DEFAULT_BASE_URL

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore = TokenStore(context)

    @Provides
    @Singleton
    fun provideSessionEvents(): SessionEvents = SessionEvents()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        tokenStore: TokenStore,
        sessionEvents: SessionEvents,
        @Named("baseUrl") baseUrl: String,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenStore))
        .authenticator(AuthAuthenticator(baseUrl, tokenStore, sessionEvents))
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json, @Named("baseUrl") baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)
}
