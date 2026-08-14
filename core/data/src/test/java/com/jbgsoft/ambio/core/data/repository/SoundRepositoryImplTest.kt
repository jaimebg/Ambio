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
import kotlinx.coroutines.flow.flow
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

        // Deliberately not a plausible future sound name: "thunder" used to stand
        // in for "unknown" here and silently stopped testing anything the day it
        // joined the catalogue.
        repository.setSoundActive("not_a_sound", active = true)

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
    fun `at most three sounds can be active at once`() = runTest {
        val repository = repositoryStoring("rain")

        listOf("fireplace", "forest", "ocean", "cave")
            .forEach { repository.setSoundActive(it, active = true) }

        val mix = repository.getActiveMix().first()
        assertThat(mix).hasSize(3)
        assertThat(mix.map { it.sound.id })
            .containsExactly("rain", "fireplace", "forest").inOrder()
    }

    @Test
    fun `overlapping activations do not lose a toggle`() = runTest {
        val repository = repositoryStoring("rain")
        // The *read* is what has to be able to suspend.
        //
        // Slowing the store write down instead proves nothing: persist() publishes to
        // mixOverride before it writes, so the second coroutine reads the first one's
        // result whether or not a mutex exists. And with the flowOf(...) this class
        // uses everywhere else, .first() never suspends at all, so the first coroutine
        // runs start to finish before the second is even dispatched. Under either of
        // those the assertion below holds with the mutex deleted.
        //
        // A read that suspends is what puts both coroutines inside the
        // read-modify-write at once — the situation two rapid taps create, each on its
        // own viewModelScope.launch — and the only thing that gets them out of it with
        // both toggles intact is the mutex.
        every { dataStore.preferences } returns flow {
            delay(10)
            emit(UserPreferences(lastMix = "rain"))
        }

        val first = launch { repository.setSoundActive("cave", active = true) }
        val second = launch { repository.setSoundActive("ocean", active = true) }
        first.join()
        second.join()

        assertThat(repository.getActiveMix().first().map { it.sound.id })
            .containsExactly("rain", "cave", "ocean")
    }

    @Test
    fun `a level write overlapping an activation loses neither`() = runTest {
        // setSoundLevel does the same read-modify-write and needs the same lock: a
        // level landing on a snapshot taken before an activation writes the mix back
        // without the sound that was just added.
        val repository = repositoryStoring("rain")
        every { dataStore.preferences } returns flow {
            delay(10)
            emit(UserPreferences(lastMix = "rain"))
        }

        val activation = launch { repository.setSoundActive("cave", active = true) }
        val levelChange = launch { repository.setSoundLevel("rain", 0.25f) }
        activation.join()
        levelChange.join()

        val mix = repository.getActiveMix().first()
        assertThat(mix.map { it.sound.id }).containsExactly("rain", "cave").inOrder()
        assertThat(mix.single { it.sound.id == "rain" }.level).isEqualTo(0.25f)
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

    @Test
    fun `activating a fourth sound does nothing`() = runTest {
        // rain is catalog-EARLIER than all three active sounds, so this fixture
        // discriminates: without the repository guard, persist()'s truncation
        // would admit rain and drop ocean, giving [rain, fireplace, forest].
        val repository = repositoryStoring("fireplace,forest,ocean")

        repository.setSoundActive("rain", active = true)

        val mix = repository.getActiveMix().first()
        assertThat(mix).hasSize(3)
        assertThat(mix.map { it.sound.id })
            .containsExactly("fireplace", "forest", "ocean").inOrder()
    }

    @Test
    fun `a sound already in a full mix can still be deactivated`() = runTest {
        val repository = repositoryStoring("rain,fireplace,forest")

        repository.setSoundActive("forest", active = false)

        assertThat(repository.getActiveMix().first().map { it.sound.id })
            .containsExactly("rain", "fireplace").inOrder()
    }

    @Test
    fun `setting the level of a sound in a full mix still works`() = runTest {
        val repository = repositoryStoring("rain,fireplace,forest")

        repository.setSoundLevel("fireplace", 0.4f)

        assertThat(repository.getActiveMix().first().single { it.sound.id == "fireplace" }.level)
            .isEqualTo(0.4f)
    }

    @Test
    fun `swapping one sound for another works at the ceiling`() = runTest {
        // The real flow once a card is disabled: remove, then add.
        val repository = repositoryStoring("rain,fireplace,forest")

        repository.setSoundActive("forest", active = false)
        repository.setSoundActive("ocean", active = true)

        assertThat(repository.getActiveMix().first().map { it.sound.id })
            .containsExactly("rain", "fireplace", "ocean").inOrder()
    }
}
