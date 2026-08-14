package com.jbgsoft.ambio.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.jbgsoft.ambio.core.common.resources.PLAY_TO_RESOURCE_QUALIFIER
import com.jbgsoft.ambio.core.common.resources.resourceDirFor
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Guards the one localization mistake nothing else catches: a resource
 * directory whose qualifier Android cannot parse, or which is spelled for a
 * locale the resolver never asks about. Lint's `MissingTranslation` compares
 * *declared* locales, and a directory the resolver never reads is not a
 * declared locale — so the locale silently ships in English.
 *
 * Two tests are needed, because neither covers the other:
 *
 *  - This one reads a string back under each locale and catches a directory
 *    that fell through to the **default**. It cannot catch one that fell
 *    through to a **same-language sibling**: Android's cross-region fallback
 *    resolves `fr-CA` to `values-fr`, and since `sound_rain` is "Pluie" in
 *    both, a broken `values-fr-rCA` reads back correctly. Nine of the 47
 *    locales sit in such a group (es/es-419, fr/fr-CA, pt-BR/pt-PT, and the
 *    three Chinese variants), and `values-es` / `values-b+es+419` are byte
 *    identical, so no assertion on content could ever tell them apart.
 *  - [LocaleDirectorySetTest] therefore checks the directory *names* against
 *    the map. That is the only possible gate for the sibling groups.
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

                // en-GB legitimately shares English's wording for all twelve of
                // these nouns, so it is skipped rather than asserted on. In a
                // module where en-GB carries real British spellings, replace
                // this skip with a positive assertion on a diverging string.
                if (playCode != "en-GB" && rain == "Rain") {
                    stillEnglish += "$playCode -> values-$qualifier"
                }
            }

        // Without this the assertion below would also pass on an empty sweep.
        assertThat(swept).isEqualTo(47)
        assertThat(stillEnglish).isEmpty()
    }
}

/**
 * Checks that the locale directories on disk are named exactly what the locale
 * map says they should be.
 *
 * This is the only gate for the nine locales in a same-language fallback group,
 * where a misspelled qualifier is absorbed by a sibling and every readable
 * string still comes back correct — see [SoundNameLocalizationTest]. It needs
 * no Robolectric: the mistake is in the directory name, which is readable
 * without starting an Android runtime.
 */
class LocaleDirectorySetTest {

    // AGP runs unit tests with the module directory as the working directory,
    // the same assumption AmbioManifestTest makes to read the source manifest.
    private val resDir = File("src/main/res")

    private fun localeDirs(): List<String> {
        assertWithMessage(
            "src/main/res not found from ${File("").absolutePath} — the unit " +
                "test working directory is no longer the module directory"
        ).that(resDir.isDirectory).isTrue()

        return resDir.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") }
            .map { it.name }
    }

    @Test
    fun `ships one locale directory per mapped locale, named exactly as the map says`() {
        val expected = PLAY_TO_RESOURCE_QUALIFIER
            .filterValues { it.isNotEmpty() }
            .keys
            .map { resourceDirFor(it) }

        assertThat(localeDirs()).containsExactlyElementsIn(expected)
    }

    @Test
    fun `every locale directory carries a strings file`() {
        val empty = localeDirs().filter { !File(resDir, "$it/strings.xml").isFile }

        assertThat(empty).isEmpty()
    }
}
