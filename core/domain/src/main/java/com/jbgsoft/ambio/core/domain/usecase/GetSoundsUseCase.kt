package com.jbgsoft.ambio.core.domain.usecase

import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import javax.inject.Inject

class GetSoundsUseCase @Inject constructor(
    private val soundRepository: SoundRepository
) {
    operator fun invoke(): List<Sound> = soundRepository.getAllSounds()
}
