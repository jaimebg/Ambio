package com.jbgsoft.ambio.core.data.repository

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WaterDrop
import com.jbgsoft.ambio.core.data.datastore.PreferencesDataStore
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundRepositoryImpl @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore
) : SoundRepository {

    private val sounds = listOf(
        Sound(
            id = "rain",
            name = "Rain",
            description = "Gentle rain on a window",
            icon = Icons.Default.WaterDrop,
            audioRes = com.jbgsoft.ambio.core.data.R.raw.rain_loop,
            illustrationRes = com.jbgsoft.ambio.core.data.R.drawable.illustration_rain,
            theme = SoundTheme.RAIN
        ),
        Sound(
            id = "fireplace",
            name = "Fireplace",
            description = "Crackling fireplace warmth",
            icon = Icons.Default.LocalFireDepartment,
            audioRes = com.jbgsoft.ambio.core.data.R.raw.fireplace_loop,
            illustrationRes = com.jbgsoft.ambio.core.data.R.drawable.illustration_fireplace,
            theme = SoundTheme.FIREPLACE
        ),
        Sound(
            id = "forest",
            name = "Forest",
            description = "Peaceful forest ambiance",
            icon = Icons.Default.Forest,
            audioRes = com.jbgsoft.ambio.core.data.R.raw.forest_loop,
            illustrationRes = com.jbgsoft.ambio.core.data.R.drawable.illustration_forest,
            theme = SoundTheme.FOREST
        ),
        Sound(
            id = "ocean",
            name = "Ocean",
            description = "Calm ocean waves",
            icon = Icons.Default.Waves,
            audioRes = com.jbgsoft.ambio.core.data.R.raw.ocean_loop,
            illustrationRes = com.jbgsoft.ambio.core.data.R.drawable.illustration_ocean,
            theme = SoundTheme.OCEAN
        ),
        Sound(
            id = "wind",
            name = "Wind",
            description = "Soft wind through leaves",
            icon = Icons.Default.Air,
            audioRes = com.jbgsoft.ambio.core.data.R.raw.wind_loop,
            illustrationRes = com.jbgsoft.ambio.core.data.R.drawable.illustration_wind,
            theme = SoundTheme.WIND
        )
    )

    private val selectedSoundIdFlow = MutableStateFlow("rain")

    override fun getAllSounds(): List<Sound> = sounds

    override fun getSoundById(id: String): Sound? = sounds.find { it.id == id }

    override fun getSelectedSound(): Flow<Sound> = combine(
        selectedSoundIdFlow,
        preferencesDataStore.preferences
    ) { currentId, prefs ->
        getSoundById(currentId) ?: getSoundById(prefs.lastSoundId) ?: sounds.first()
    }

    override suspend fun setSelectedSound(soundId: String) {
        selectedSoundIdFlow.value = soundId
    }
}
