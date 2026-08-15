package com.jbgsoft.ambio.core.data.repository

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.FlutterDash
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WaterDrop
import com.jbgsoft.ambio.core.data.datastore.PreferencesDataStore
import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.MixCodec
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.SoundGlow
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

/**
 * Order matters twice over: it is the order the picker draws, and
 * [MixCodec.encode] emits ids in list order so that the same mix always
 * produces the same string. Grouped by theme family rather than by when each
 * was added, so related sounds sit together in the grid.
 *
 * Several sounds deliberately share a theme: birds and crickets are forest's
 * because they are what a forest sounds like at either end of the day, cafe
 * borrows fireplace's warm indoor palette, and stream sits with ocean.
 * Sharing costs nothing and keeps the palette space small enough that
 * ThemeContrastTest can still enumerate every reachable mix.
 *
 * Glows are the opposite: never shared, one per sound. The theme drives
 * Material's flat colours, where sharing is free; the glow drives the mix
 * gradient, where two sounds holding the same colour renders a flat screen.
 */
val SOUND_CATALOGUE: List<Sound> = listOf(
    Sound(
        id = "rain",
        nameRes = com.jbgsoft.ambio.core.data.R.string.sound_rain,
        icon = Icons.Default.WaterDrop,
        audioRes = com.jbgsoft.ambio.core.data.R.raw.rain_loop,
        theme = SoundTheme.RAIN,
        glow = SoundGlow.RAIN
    ),
    Sound(
        id = "fireplace",
        nameRes = com.jbgsoft.ambio.core.data.R.string.sound_fireplace,
        icon = Icons.Default.LocalFireDepartment,
        audioRes = com.jbgsoft.ambio.core.data.R.raw.fireplace_loop,
        theme = SoundTheme.FIREPLACE,
        glow = SoundGlow.FIREPLACE
    ),
    Sound(
        id = "cafe",
        nameRes = com.jbgsoft.ambio.core.data.R.string.sound_cafe,
        icon = Icons.Default.LocalCafe,
        audioRes = com.jbgsoft.ambio.core.data.R.raw.cafe_loop,
        theme = SoundTheme.FIREPLACE,
        glow = SoundGlow.CAFE
    ),
    Sound(
        id = "forest",
        nameRes = com.jbgsoft.ambio.core.data.R.string.sound_forest,
        icon = Icons.Default.Forest,
        audioRes = com.jbgsoft.ambio.core.data.R.raw.forest_loop,
        theme = SoundTheme.FOREST,
        glow = SoundGlow.FOREST
    ),
    Sound(
        id = "birds",
        nameRes = com.jbgsoft.ambio.core.data.R.string.sound_birds,
        icon = Icons.Default.FlutterDash,
        audioRes = com.jbgsoft.ambio.core.data.R.raw.birds_loop,
        theme = SoundTheme.FOREST,
        glow = SoundGlow.BIRDS
    ),
    Sound(
        id = "crickets",
        nameRes = com.jbgsoft.ambio.core.data.R.string.sound_crickets,
        icon = Icons.Default.NightsStay,
        audioRes = com.jbgsoft.ambio.core.data.R.raw.crickets_loop,
        theme = SoundTheme.FOREST,
        glow = SoundGlow.CRICKETS
    ),
    Sound(
        id = "ocean",
        nameRes = com.jbgsoft.ambio.core.data.R.string.sound_ocean,
        icon = Icons.Default.Waves,
        audioRes = com.jbgsoft.ambio.core.data.R.raw.ocean_loop,
        theme = SoundTheme.OCEAN,
        glow = SoundGlow.OCEAN
    ),
    Sound(
        id = "stream",
        nameRes = com.jbgsoft.ambio.core.data.R.string.sound_stream,
        icon = Icons.Default.Water,
        audioRes = com.jbgsoft.ambio.core.data.R.raw.stream_loop,
        theme = SoundTheme.OCEAN,
        glow = SoundGlow.STREAM
    ),
    Sound(
        id = "cave",
        nameRes = com.jbgsoft.ambio.core.data.R.string.sound_cave,
        icon = Icons.Default.Terrain,
        audioRes = com.jbgsoft.ambio.core.data.R.raw.cave_loop,
        theme = SoundTheme.CAVE,
        glow = SoundGlow.CAVE
    ),
    Sound(
        id = "wind",
        nameRes = com.jbgsoft.ambio.core.data.R.string.sound_wind,
        icon = Icons.Default.Air,
        audioRes = com.jbgsoft.ambio.core.data.R.raw.wind_loop,
        theme = SoundTheme.CAVE,
        glow = SoundGlow.WIND
    ),
    Sound(
        id = "white_noise",
        nameRes = com.jbgsoft.ambio.core.data.R.string.sound_white_noise,
        icon = Icons.Default.GraphicEq,
        audioRes = com.jbgsoft.ambio.core.data.R.raw.white_noise_loop,
        theme = SoundTheme.NOISE,
        glow = SoundGlow.WHITE_NOISE
    ),
    Sound(
        id = "brown_noise",
        nameRes = com.jbgsoft.ambio.core.data.R.string.sound_brown_noise,
        icon = Icons.Default.BlurOn,
        audioRes = com.jbgsoft.ambio.core.data.R.raw.brown_noise_loop,
        theme = SoundTheme.NOISE,
        glow = SoundGlow.BROWN_NOISE
    )
)

@Singleton
class SoundRepositoryImpl @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore
) : SoundRepository {

    private val sounds = SOUND_CATALOGUE

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
