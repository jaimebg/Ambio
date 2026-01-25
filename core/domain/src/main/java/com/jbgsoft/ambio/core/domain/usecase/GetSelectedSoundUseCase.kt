package com.jbgsoft.ambio.core.domain.usecase

import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSelectedSoundUseCase @Inject constructor(
    private val soundRepository: SoundRepository
) {
    operator fun invoke(): Flow<Sound> = soundRepository.getSelectedSound()
}
