package com.jbgsoft.ambio.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreferencesDataStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun newPreferencesDataStore(): PreferencesDataStore {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tmpFolder.newFile("test.preferences_pb") }
        )
        return PreferencesDataStore(dataStore)
    }

    @Test
    fun `the three toggles default to enabled`() = runTest {
        val prefs = newPreferencesDataStore().preferences.first()
        assertThat(prefs.hapticsEnabled).isTrue()
        assertThat(prefs.chimeEnabled).isTrue()
        assertThat(prefs.effectsEnabled).isTrue()
    }

    @Test
    fun `disabling haptics persists and leaves the others alone`() = runTest {
        val dataStore = newPreferencesDataStore()
        dataStore.setHapticsEnabled(false)

        val prefs = dataStore.preferences.first()
        assertThat(prefs.hapticsEnabled).isFalse()
        assertThat(prefs.chimeEnabled).isTrue()
        assertThat(prefs.effectsEnabled).isTrue()
    }

    @Test
    fun `disabling chime does not disturb session state`() = runTest {
        val dataStore = newPreferencesDataStore()
        dataStore.setVolume(0.42f)
        dataStore.setChimeEnabled(false)

        val prefs = dataStore.preferences.first()
        assertThat(prefs.chimeEnabled).isFalse()
        assertThat(prefs.volume).isEqualTo(0.42f)
    }
}
