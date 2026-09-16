package com.jbgsoft.ambio.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.PlanStep
import com.jbgsoft.ambio.core.domain.model.SessionPlan
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

    @Test
    fun `the stored mix survives a write and reads back verbatim`() = runTest {
        val dataStore = newPreferencesDataStore()

        dataStore.setLastMix("rain:1.00,fireplace:0.60")

        assertThat(dataStore.preferences.first().lastMix).isEqualTo("rain:1.00,fireplace:0.60")
    }

    @Test
    fun `the mix defaults to rain when nothing was ever stored`() = runTest {
        assertThat(newPreferencesDataStore().preferences.first().lastMix).isEqualTo("rain")
    }

    @Test
    fun `the break mix is absent until it is written`() = runTest {
        val prefs = newPreferencesDataStore().preferences.first()
        assertThat(prefs.breakMix).isNull()
    }

    @Test
    fun `writing the break mix leaves the focus mix alone`() = runTest {
        val dataStore = newPreferencesDataStore()
        dataStore.setLastMix("rain:1.00")
        dataStore.setBreakMix("ocean:0.50")

        val prefs = dataStore.preferences.first()
        assertThat(prefs.lastMix).isEqualTo("rain:1.00")
        assertThat(prefs.breakMix).isEqualTo("ocean:0.50")
    }

    @Test
    fun `break sound is off by default and persists when enabled`() = runTest {
        val dataStore = newPreferencesDataStore()
        assertThat(dataStore.preferences.first().breakSoundEnabled).isFalse()

        dataStore.setBreakSoundEnabled(true)

        assertThat(dataStore.preferences.first().breakSoundEnabled).isTrue()
    }

    @Test
    fun `the sleep timer is off by default and persists a duration`() = runTest {
        val dataStore = newPreferencesDataStore()
        assertThat(dataStore.preferences.first().sleepMinutes).isEqualTo(0)

        dataStore.setSleepMinutes(30)

        assertThat(dataStore.preferences.first().sleepMinutes).isEqualTo(30)
    }

    @Test
    fun `the session plan defaults and round-trips`() = runTest {
        val dataStore = newPreferencesDataStore()
        assertThat(dataStore.preferences.first().sessionPlan).isEqualTo(SessionPlan.DEFAULT)

        val plan = SessionPlan(listOf(PlanStep.Focus(35), PlanStep.Break(15)), repeat = true)
        dataStore.setSessionPlan(plan)

        assertThat(dataStore.preferences.first().sessionPlan).isEqualTo(plan)
    }
}
