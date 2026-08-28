package com.smsforwarder.gateway.di

import android.content.Context
import androidx.room.Room
import com.smsforwarder.gateway.data.local.GatewayConfigStore
import com.smsforwarder.gateway.data.local.db.GatewayDatabase
import com.smsforwarder.gateway.data.local.db.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GatewayDatabase =
        Room.databaseBuilder(context, GatewayDatabase::class.java, "gateway.db")
            .addMigrations(GatewayDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideMessageDao(database: GatewayDatabase): MessageDao = database.messageDao()

    @Provides
    @Singleton
    fun provideGatewayConfigStore(@ApplicationContext context: Context): GatewayConfigStore =
        GatewayConfigStore(context)
}
