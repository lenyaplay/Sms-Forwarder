package com.smsforwarder.gateway.di

import android.content.Context
import androidx.room.Room
import com.smsforwarder.gateway.data.local.GatewayConfigStore
import com.smsforwarder.gateway.data.local.db.DeliveryLogDao
import com.smsforwarder.gateway.data.local.db.FilterRuleDao
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
            .addMigrations(
                GatewayDatabase.MIGRATION_1_2,
                GatewayDatabase.MIGRATION_2_3,
                GatewayDatabase.MIGRATION_3_4,
                GatewayDatabase.MIGRATION_4_5,
            )
            .build()

    @Provides
    fun provideMessageDao(database: GatewayDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideFilterRuleDao(database: GatewayDatabase): FilterRuleDao = database.filterRuleDao()

    @Provides
    fun provideDeliveryLogDao(database: GatewayDatabase): DeliveryLogDao = database.deliveryLogDao()

    @Provides
    @Singleton
    fun provideGatewayConfigStore(@ApplicationContext context: Context): GatewayConfigStore =
        GatewayConfigStore(context)
}
