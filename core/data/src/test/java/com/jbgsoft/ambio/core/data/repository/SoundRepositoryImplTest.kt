package com.jbgsoft.ambio.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.data.datastore.PreferencesDataStore
import com.jbgsoft.ambio.core.domain.model.UserPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SoundRepositoryImplTest {

    private fun repositoryStoring(lastMix: String): SoundRepositoryImpl {
        val dataStore = mockk<PreferencesDataStore>()
        every { dataStore.preferences } returns flowOf(UserPreferences(lastMix = lastMix))
        return SoundRepositoryImpl(dataStore)
    }

    @Test
    fun `selected sound comes from the stored preference when nothing was selected yet`() = runTest {
        val repository = repositoryStoring("forest")

        assertThat(repository.getSelectedSound().first().id).isEqualTo("forest")
    }

    @Test
    fun `an explicit selection wins over the stored preference`() = runTest {
        val repository = repositoryStoring("forest")

        repository.setSelectedSound("ocean")

        assertThat(repository.getSelectedSound().first().id).isEqualTo("ocean")
    }

    @Test
    fun `an unknown stored id falls back to the first sound`() = runTest {
        val repository = repositoryStoring("wind")

        assertThat(repository.getSelectedSound().first().id).isEqualTo("rain")
    }
}
