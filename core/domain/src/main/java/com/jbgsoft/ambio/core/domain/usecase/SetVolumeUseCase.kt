package com.jbgsoft.ambio.core.domain.usecase

import com.jbgsoft.ambio.core.domain.repository.PreferencesRepository
import javax.inject.Inject

class SetVolumeUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(volume: Float) {
        preferencesRepository.setVolume(volume.coerceIn(0f, 1f))
    }
}
