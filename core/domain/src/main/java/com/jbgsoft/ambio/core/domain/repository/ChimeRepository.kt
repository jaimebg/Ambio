package com.jbgsoft.ambio.core.domain.repository

import androidx.annotation.RawRes

/**
 * Repository interface for accessing notification sound resources.
 * Provides resource IDs for chime/notification sounds used throughout the app.
 */
interface ChimeRepository {
    /**
     * Get the raw resource ID for the timer completion chime sound.
     */
    @RawRes
    fun getTimerChimeResource(): Int

    /**
     * Get the raw resource ID for the sound played when a whole session plan ends.
     */
    @RawRes
    fun getSuccessChimeResource(): Int
}
