package com.jbgsoft.ambio.core.di

import android.content.Context
import com.jbgsoft.ambio.core.common.haptics.HapticManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideHapticManager(
        @ApplicationContext context: Context
    ): HapticManager = HapticManager(context)
}
