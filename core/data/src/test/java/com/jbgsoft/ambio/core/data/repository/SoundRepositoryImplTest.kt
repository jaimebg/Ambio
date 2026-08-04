package com.jbgsoft.ambio.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.data.datastore.PreferencesDataStore
import com.jbgsoft.ambio.core.domain.model.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

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
}
