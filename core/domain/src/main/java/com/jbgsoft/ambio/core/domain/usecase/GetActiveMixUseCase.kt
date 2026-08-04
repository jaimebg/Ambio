package com.jbgsoft.ambio.core.domain.usecase

import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetActiveMixUseCase @Inject constructor(
    private val soundRepository: SoundRepository
) {
    operator fun invoke(): Flow<List<ActiveSound>> = soundRepository.getActiveMix()
}
