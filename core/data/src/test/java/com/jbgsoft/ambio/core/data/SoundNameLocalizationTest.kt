package com.jbgsoft.ambio.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.common.resources.PLAY_TO_RESOURCE_QUALIFIER
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Guards the one localization mistake nothing else catches: a resource
 * directory whose qualifier Android cannot parse. Lint's `MissingTranslation`
 * compares *declared* locales, and a directory the resolver never reads is not
 * a declared locale — so the locale silently ships in English.
 *
 * SDK pinned to 34: Robolectric 4.16.1 supports at most 36, and its API 36
 * shadow needs Java 21 while this toolchain is 17.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SoundNameLocalizationTest {

    @Test
    fun `every mapped locale loads its own translations`() {
        val stillEnglish = mutableListOf<String>()
        var swept = 0

        PLAY_TO_RESOURCE_QUALIFIER
            .filterValues { it.isNotEmpty() }
            .forEach { (playCode, qualifier) ->
                RuntimeEnvironment.setQualifiers(qualifier)
                val context: Context = ApplicationProvider.getApplicationContext()
                val rain = context.getString(R.string.sound_rain)
                swept++

                // en-GB legitimately shares English's wording. For every other
                // locale, reading "Rain" back means Android never loaded the
                // directory — the qualifier is wrong and the locale is dead.
                if (playCode != "en-GB" && rain == "Rain") {
                    stillEnglish += "$playCode -> values-$qualifier"
                }
            }

        // Without this the assertion below would also pass on an empty sweep.
        assertThat(swept).isEqualTo(47)
        assertThat(stillEnglish).isEmpty()
    }
}
