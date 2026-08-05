package com.jbgsoft.ambio.core.di

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import com.jbgsoft.ambio.media.MixEntry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Unit tests for RepositoryMixSource.currentMix(), the ActiveSound -> MixEntry mapping
 * that is the only thing in core:di this addition needed covered: it is pure, it is the
 * one place that sees both SoundRepository (core:domain) and MixEntry (media), and it is
 * what AudioService.loadStoredMixAndPlay calls on a cold start.
 *
 * The empty case matters as much as the populated one: an empty result from here is
 * exactly what drives that method's stopSelf() branch. SoundRepository.getActiveMix()
 * documents that it never emits an empty list, but that is a contract on the
 * *repository*, not a guarantee this mapping can lean on — it is tested here anyway so
 * the stopSelf() path stays proven from a fake that does emit one, rather than merely
 * assumed safe by inherited convention.
 */
class RepositoryMixSourceTest {

    private val rain = Sound(
        id = "rain",
        nameRes = 1,
        icon = Icons.Default.WaterDrop,
        audioRes = 101,
        illustrationRes = 2,
        theme = SoundTheme.RAIN
    )

    private val forest = Sound(
        id = "forest",
        nameRes = 3,
        icon = Icons.Default.WaterDrop,
        audioRes = 102,
        illustrationRes = 4,
        theme = SoundTheme.FOREST
    )

    private fun repositoryWith(mix: List<ActiveSound>): SoundRepository = mockk {
        every { getActiveMix() } returns flowOf(mix)
    }

    @Test
    fun `a multi-sound mix maps every entry with its soundId, audioRes and level`() = runTest {
        val mixSource = RepositoryMixSource(
            repositoryWith(
                listOf(
                    ActiveSound(rain, 1.0f),
                    ActiveSound(forest, 0.4f)
                )
            )
        )

        val result = mixSource.currentMix()

        assertThat(result).containsExactly(
            MixEntry(soundId = "rain", audioRes = 101, level = 1.0f),
            MixEntry(soundId = "forest", audioRes = 102, level = 0.4f)
        ).inOrder()
    }

    @Test
    fun `an empty mix maps to an empty list`() = runTest {
        // This is the input that drives AudioService.loadStoredMixAndPlay's stopSelf()
        // branch, not a throwaway edge case.
        val mixSource = RepositoryMixSource(repositoryWith(emptyList()))

        val result = mixSource.currentMix()

        assertThat(result).isEmpty()
    }
}
