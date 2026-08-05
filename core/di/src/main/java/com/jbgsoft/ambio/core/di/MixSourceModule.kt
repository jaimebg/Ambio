package com.jbgsoft.ambio.core.di

import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import com.jbgsoft.ambio.media.MixEntry
import com.jbgsoft.ambio.media.MixSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the stored mix into the media module.
 *
 * This lives in core:di because it is the only module that sees both sides:
 * SoundRepository in core:domain, and MixEntry in media. The media module declares no
 * project dependency at all, and this is what lets that stay true while the service
 * still gets a mix to play.
 */
@Singleton
class RepositoryMixSource @Inject constructor(
    private val soundRepository: SoundRepository
) : MixSource {
    override suspend fun currentMix(): List<MixEntry> =
        soundRepository.getActiveMix().first().map { active ->
            MixEntry(
                soundId = active.sound.id,
                audioRes = active.sound.audioRes,
                level = active.level
            )
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MixSourceModule {
    @Binds
    @Singleton
    abstract fun bindMixSource(impl: RepositoryMixSource): MixSource
}
