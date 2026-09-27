package com.zcode.android.core.agent

import com.zcode.android.core.terminal.ExecService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TerminalModule {
    @Provides
    @Singleton
    fun execService(): ExecService = ExecService()
}
