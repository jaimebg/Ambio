package com.jbgsoft.ambio.core.data.repository

import androidx.annotation.RawRes
import com.jbgsoft.ambio.core.data.R
import com.jbgsoft.ambio.core.domain.repository.ChimeRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChimeRepositoryImpl @Inject constructor() : ChimeRepository {

    @RawRes
    override fun getTimerChimeResource(): Int = R.raw.timer_chime
}
