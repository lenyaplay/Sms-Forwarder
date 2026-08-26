package com.smsforwarder.viewer.di

import android.content.Context
import com.smsforwarder.viewer.data.local.ServerConfigStore
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

    // 10.0.2.2 is the emulator's alias for the host machine's 127.0.0.1 - used
    // only as a last-resort fallback before the user has configured a real
    // server URL (spec 0010's "Server setup" screen). A physical device needs
    // `adb reverse tcp:8080 tcp:8080` if pointed at a local backend.
    private const val FALLBACK_BASE_URL = "http://10.0.2.2:8080/"

    @Provides
    @Singleton
    fun provideServerConfigStore(@ApplicationContext context: Context): ServerConfigStore = ServerConfigStore(context)

    // Retrofit.Builder().baseUrl() only validates URL *format*, it never
    // connects - so it's safe to hand it a fallback here even when the user
    // hasn't configured a real server yet. Routing to the mandatory "Server
    // setup" screen is driven separately by ServerConfigStore.hasUrl() in
    // NavGraph, not by this provider - if this threw or returned null instead,
    // the whole Hilt graph (built eagerly at Activity start) would crash
    // before the user ever saw that screen.
    @Provides
    @Singleton
    @Named("baseUrl")
    fun provideBaseUrl(serverConfigStore: ServerConfigStore): String = serverConfigStore.getUrl() ?: FALLBACK_BASE_URL

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
