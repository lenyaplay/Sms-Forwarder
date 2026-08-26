package com.smsforwarder.viewer.di

import com.smsforwarder.viewer.data.remote.SseClient
import com.smsforwarder.viewer.data.remote.SseSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideSseClient(json: Json): SseSource {
        // A plain (non-authenticating) client: access_token travels as a
        // query param for GET /events (see spec 0006), not a header.
        return SseClient(OkHttpClient.Builder().build(), json)
    }
}
