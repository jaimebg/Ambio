package com.jbgsoft.ambio.core.di

import android.content.Context
import androidx.room.Room
import com.jbgsoft.ambio.core.data.local.dao.SessionDao
import com.jbgsoft.ambio.core.data.local.db.AmbioDatabase
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
    fun provideAmbioDatabase(
        @ApplicationContext context: Context
    ): AmbioDatabase = Room.databaseBuilder(
        context,
        AmbioDatabase::class.java,
        AmbioDatabase.DATABASE_NAME
    ).build()

    @Provides
    @Singleton
    fun provideSessionDao(database: AmbioDatabase): SessionDao = database.sessionDao()
}
