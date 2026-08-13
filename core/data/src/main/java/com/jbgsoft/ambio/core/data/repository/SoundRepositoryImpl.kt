package com.jbgsoft.ambio.core.data.repository

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WaterDrop
import com.jbgsoft.ambio.core.data.datastore.PreferencesDataStore
import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.MixCodec
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundRepositoryImpl @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore
) : SoundRepository {

    private val sounds = listOf(
        Sound(
            id = "rain",
            nameRes = com.jbgsoft.ambio.core.data.R.string.sound_rain,
            icon = Icons.Default.WaterDrop,
            audioRes = com.jbgsoft.ambio.core.data.R.raw.rain_loop,
            illustrationRes = com.jbgsoft.ambio.core.data.R.drawable.illustration_rain,
            theme = SoundTheme.RAIN
        ),
        Sound(
            id = "fireplace",
            nameRes = com.jbgsoft.ambio.core.data.R.string.sound_fireplace,
            icon = Icons.Default.LocalFireDepartment,
            audioRes = com.jbgsoft.ambio.core.data.R.raw.fireplace_loop,
            illustrationRes = com.jbgsoft.ambio.core.data.R.drawable.illustration_fireplace,
            theme = SoundTheme.FIREPLACE
        ),
        Sound(
            id = "forest",
            nameRes = com.jbgsoft.ambio.core.data.R.string.sound_forest,
            icon = Icons.Default.Forest,
            audioRes = com.jbgsoft.ambio.core.data.R.raw.forest_loop,
            illustrationRes = com.jbgsoft.ambio.core.data.R.drawable.illustration_forest,
            theme = SoundTheme.FOREST
        ),
        Sound(
            id = "ocean",
            nameRes = com.jbgsoft.ambio.core.data.R.string.sound_ocean,
            icon = Icons.Default.Waves,
            audioRes = com.jbgsoft.ambio.core.data.R.raw.ocean_loop,
            illustrationRes = com.jbgsoft.ambio.core.data.R.drawable.illustration_ocean,
            theme = SoundTheme.OCEAN
        ),
        Sound(
            id = "cave",
            nameRes = com.jbgsoft.ambio.core.data.R.string.sound_cave,
            icon = Icons.Default.Terrain,
            audioRes = com.jbgsoft.ambio.core.data.R.raw.cave_loop,
            illustrationRes = com.jbgsoft.ambio.core.data.R.drawable.illustration_cave,
            theme = SoundTheme.CAVE
        )
    )

    private val mixOverride = MutableStateFlow<String?>(null)

    // Serializes the read-modify-write in setSoundActive/setSoundLevel so two
    // overlapping calls (rapid taps, each its own viewModelScope.launch) can't
    // both read the same snapshot and have the second silently drop the first.
    private val mutex = Mutex()

    override fun getAllSounds(): List<Sound> = sounds

    override fun getSoundById(id: String): Sound? = sounds.find { it.id == id }

    override fun getActiveMix(): Flow<List<ActiveSound>> = combine(
        mixOverride,
        preferencesDataStore.preferences
    ) { override, prefs ->
        MixCodec.decode(override ?: prefs.lastMix, sounds)
    }

    override suspend fun setSoundActive(soundId: String, active: Boolean) {
        if (getSoundById(soundId) == null) return
        mutex.withLock {
            val current = currentMix()
            val updated = when {
                active && current.any { it.sound.id == soundId } -> current
                // The mix never holds more than MAX_ACTIVE_SOUNDS.
                active && current.size >= MixCodec.MAX_ACTIVE_SOUNDS -> return
                active -> current + ActiveSound(getSoundById(soundId)!!, 1.0f)
                // The mix is never empty.
                current.size == 1 -> return
                else -> current.filterNot { it.sound.id == soundId }
            }
            persist(updated)
        }
    }

    override suspend fun setSoundLevel(soundId: String, level: Float) {
        mutex.withLock {
            val current = currentMix()
            if (current.none { it.sound.id == soundId }) return
            persist(
                current.map { active ->
                    if (active.sound.id == soundId) active.copy(level = level.coerceIn(0f, 1f))
                    else active
                }
            )
        }
    }

    private suspend fun currentMix(): List<ActiveSound> =
        MixCodec.decode(mixOverride.value ?: preferencesDataStore.preferences.first().lastMix, sounds)

    /**
     * Updates [mixOverride] optimistically so the caller's own toggle is visible
     * without waiting a frame for DataStore, but rolls it back if the store write
     * fails — a failed write must never leave the override claiming a mix the
     * store doesn't hold. The exception still propagates to the caller.
     */
    private suspend fun persist(mix: List<ActiveSound>) {
        val encoded = MixCodec.encode(
            // Re-decoding normalises the order before it is written or observed.
            MixCodec.decode(MixCodec.encode(mix, withLevels = true), sounds),
            withLevels = true
        )
        val previous = mixOverride.value
        mixOverride.value = encoded
        try {
            preferencesDataStore.setLastMix(encoded)
        } catch (e: Exception) {
            mixOverride.value = previous
            throw e
        }
    }
}
