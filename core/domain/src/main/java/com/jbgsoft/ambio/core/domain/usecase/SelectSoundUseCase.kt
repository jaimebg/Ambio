package com.jbgsoft.ambio.core.domain.usecase

import com.jbgsoft.ambio.core.domain.repository.PreferencesRepository
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import javax.inject.Inject

class SelectSoundUseCase @Inject constructor(
    private val soundRepository: SoundRepository,
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(soundId: String) {
        soundRepository.setSelectedSound(soundId)
        preferencesRepository.setLastSoundId(soundId)
    }
}
