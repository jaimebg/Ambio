package com.jbgsoft.ambio.core.di

import com.jbgsoft.ambio.core.data.repository.ChimeRepositoryImpl
import com.jbgsoft.ambio.core.data.repository.PreferencesRepositoryImpl
import com.jbgsoft.ambio.core.data.repository.SessionRepositoryImpl
import com.jbgsoft.ambio.core.data.repository.SoundRepositoryImpl
import com.jbgsoft.ambio.core.data.repository.TimerRepositoryImpl
import com.jbgsoft.ambio.core.domain.repository.ChimeRepository
import com.jbgsoft.ambio.core.domain.repository.PreferencesRepository
import com.jbgsoft.ambio.core.domain.repository.SessionRepository
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import com.jbgsoft.ambio.core.domain.repository.TimerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSoundRepository(impl: SoundRepositoryImpl): SoundRepository

    @Binds
    @Singleton
    abstract fun bindTimerRepository(impl: TimerRepositoryImpl): TimerRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    abstract fun bindChimeRepository(impl: ChimeRepositoryImpl): ChimeRepository
}
