package com.zcode.android.core.storage

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): ZcodeDatabase = Room.databaseBuilder(context, ZcodeDatabase::class.java, ZcodeDatabase.NAME).build()

    @Provides
    fun sessionDao(database: ZcodeDatabase): SessionDao = database.sessionDao()

    @Provides
    fun messageDao(database: ZcodeDatabase): MessageDao = database.messageDao()

    @Provides
    @Singleton
    fun preferences(
        @ApplicationContext context: Context,
    ): UserPreferences = UserPreferences(context)

    @Provides
    @Singleton
    fun apiKeyVault(
        @ApplicationContext context: Context,
    ): ApiKeyVault = ApiKeyVault(context)
}
