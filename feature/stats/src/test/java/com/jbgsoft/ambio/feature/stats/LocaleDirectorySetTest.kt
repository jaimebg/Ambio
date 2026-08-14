package com.jbgsoft.ambio.feature.stats

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.jbgsoft.ambio.core.common.resources.PLAY_TO_RESOURCE_QUALIFIER
import com.jbgsoft.ambio.core.common.resources.resourceDirFor
import java.io.File
import org.junit.Test

/**
 * Checks that the locale directories on disk are named exactly what the locale
 * map says they should be.
 *
 * Nothing else catches a directory whose qualifier Android cannot parse. Lint's
 * `MissingTranslation` compares *declared* locales, and an unparseable qualifier
 * is not a declared locale, so lint stays silent while the locale ships in
 * English. AAPT2 compiles a nonsense qualifier without complaint too.
 *
 * A content-based check cannot stand in for this. Android's cross-region
 * fallback resolves `fr-CA` to `values-fr`, so a misspelled `values-fr-rCA` is
 * absorbed by its sibling and every string still reads back correctly. Nine of
 * the 47 locales sit in such a group — es/es-419, fr/fr-CA, pt-BR/pt-PT and the
 * three Chinese variants — and comparing directory names is the only gate that
 * covers them.
 *
 * No Robolectric: the mistake is in a directory name, which is readable without
 * starting an Android runtime.
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
