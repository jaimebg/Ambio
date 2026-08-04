package com.jbgsoft.ambio.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.data.datastore.PreferencesDataStore
import com.jbgsoft.ambio.core.domain.model.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class SoundRepositoryImplTest {

    private val dataStore = mockk<PreferencesDataStore>(relaxed = true)

    private fun repositoryStoring(lastMix: String): SoundRepositoryImpl {
        every { dataStore.preferences } returns flowOf(UserPreferences(lastMix = lastMix))
        coEvery { dataStore.setLastMix(any()) } returns Unit
        return SoundRepositoryImpl(dataStore)
    }

    @Test
    fun `the mix comes from the stored preference before anything is toggled`() = runTest {
        val repository = repositoryStoring("forest")

        assertThat(repository.getActiveMix().first().map { it.sound.id })
            .containsExactly("forest")
    }

    @Test
    fun `a stored multi-sound mix is restored with its levels`() = runTest {
        val repository = repositoryStoring("rain:1.00,ocean:0.40")

        val mix = repository.getActiveMix().first()

        assertThat(mix.map { it.sound.id }).containsExactly("rain", "ocean").inOrder()
        assertThat(mix.map { it.level }).containsExactly(1.0f, 0.4f).inOrder()
    }

    @Test
    fun `activating a sound adds it to the mix`() = runTest {
        val repository = repositoryStoring("rain")

        repository.setSoundActive("cave", active = true)

        assertThat(repository.getActiveMix().first().map { it.sound.id })
            .containsExactly("rain", "cave").inOrder()
    }

    @Test
    fun `deactivating a sound removes it from the mix`() = runTest {
        val repository = repositoryStoring("rain,cave")

        repository.setSoundActive("rain", active = false)

        assertThat(repository.getActiveMix().first().map { it.sound.id })
            .containsExactly("cave")
    }

    @Test
    fun `deactivating the last active sound does nothing`() = runTest {
        val repository = repositoryStoring("rain")

        repository.setSoundActive("rain", active = false)

        assertThat(repository.getActiveMix().first().map { it.sound.id })
            .containsExactly("rain")
    }

    @Test
    fun `setting a level changes only that sound`() = runTest {
        val repository = repositoryStoring("rain,ocean")

        repository.setSoundLevel("ocean", 0.25f)

        val mix = repository.getActiveMix().first()
        assertThat(mix.single { it.sound.id == "ocean" }.level).isEqualTo(0.25f)
        assertThat(mix.single { it.sound.id == "rain" }.level).isEqualTo(1.0f)
    }

    @Test
    fun `a level is clamped into zero to one`() = runTest {
        val repository = repositoryStoring("rain")

        repository.setSoundLevel("rain", 4f)

        assertThat(repository.getActiveMix().first().single().level).isEqualTo(1.0f)
    }

    @Test
    fun `activating an unknown sound does nothing`() = runTest {
        val repository = repositoryStoring("rain")

        repository.setSoundActive("thunder", active = true)

        assertThat(repository.getActiveMix().first().map { it.sound.id })
            .containsExactly("rain")
    }

    @Test
    fun `every change is written back to the store with its levels`() = runTest {
        val repository = repositoryStoring("rain")

        repository.setSoundActive("cave", active = true)

        coVerify { dataStore.setLastMix("rain:1.00,cave:1.00") }
    }

    @Test
    fun `all five sounds can be active at once`() = runTest {
        val repository = repositoryStoring("rain")

        listOf("fireplace", "forest", "ocean", "cave")
            .forEach { repository.setSoundActive(it, active = true) }

        assertThat(repository.getActiveMix().first()).hasSize(5)
    }

    @Test
    fun `overlapping activations do not lose a toggle`() = runTest {
        val repository = repositoryStoring("rain")
        // Makes the store write slow enough that two concurrently-launched
        // calls actually overlap instead of trivially interleaving.
        coEvery { dataStore.setLastMix(any()) } coAnswers { delay(50) }

        val first = launch { repository.setSoundActive("cave", active = true) }
        val second = launch { repository.setSoundActive("ocean", active = true) }
        first.join()
        second.join()

        assertThat(repository.getActiveMix().first().map { it.sound.id })
            .containsExactly("rain", "cave", "ocean")
    }

    @Test
    fun `a failed write does not leave the override diverged from the store`() = runTest {
        val repository = repositoryStoring("rain")
        coEvery { dataStore.setLastMix(any()) } throws IOException("disk full")

        try {
            repository.setSoundActive("cave", active = true)
        } catch (expected: IOException) {
            // The failure is expected to surface to the caller; what matters
            // here is that the override doesn't keep claiming "cave" is active.
        }

        assertThat(repository.getActiveMix().first().map { it.sound.id })
            .containsExactly("rain")
    }
}
