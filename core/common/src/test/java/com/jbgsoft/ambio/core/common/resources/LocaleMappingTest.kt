package com.jbgsoft.ambio.core.common.resources

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocaleMappingTest {

    @Test
    fun `covers every locale the store listing ships`() {
        // 48 Play locales; en-US is the default values/ directory, so 47 are translations.
        assertThat(PLAY_TO_RESOURCE_QUALIFIER).hasSize(48)
        assertThat(PLAY_TO_BCP47).hasSize(48)
    }

    @Test
    fun `en-US is the default resource directory`() {
        assertThat(resourceDirFor("en-US")).isEqualTo("values")
    }

    @Test
    fun `drops a redundant region when the language ships only once`() {
        assertThat(resourceDirFor("de-DE")).isEqualTo("values-de")
        assertThat(resourceDirFor("ja-JP")).isEqualTo("values-ja")
        assertThat(resourceDirFor("es-ES")).isEqualTo("values-es")
    }

    @Test
    fun `keeps the region when a language ships more than one variant`() {
        assertThat(resourceDirFor("en-GB")).isEqualTo("values-en-rGB")
        assertThat(resourceDirFor("fr-CA")).isEqualTo("values-fr-rCA")
        assertThat(resourceDirFor("fr-FR")).isEqualTo("values-fr")
    }

    @Test
    fun `uses BCP-47 form where a plain qualifier cannot express the locale`() {
        // A UN region code and both Portuguese variants need the b+ form.
        assertThat(resourceDirFor("es-419")).isEqualTo("values-b+es+419")
        assertThat(resourceDirFor("pt-BR")).isEqualTo("values-b+pt+BR")
        assertThat(resourceDirFor("pt-PT")).isEqualTo("values-b+pt+PT")
        assertThat(resourceDirFor("bn-BD")).isEqualTo("values-b+bn+BD")
    }

    @Test
    fun `selects Chinese by script rather than region`() {
        assertThat(resourceDirFor("zh-CN")).isEqualTo("values-b+zh+Hans")
        assertThat(resourceDirFor("zh-TW")).isEqualTo("values-b+zh+Hant+TW")
        assertThat(resourceDirFor("zh-HK")).isEqualTo("values-b+zh+Hant+HK")
    }

    @Test
    fun `uses the legacy code where Android still requires it`() {
        // Hebrew and Indonesian changed ISO codes; Android resource resolution
        // still keys off the old ones on older API levels.
        assertThat(resourceDirFor("iw-IL")).isEqualTo("values-iw")
        assertThat(resourceDirFor("id")).isEqualTo("values-b+id")
    }

    @Test
    fun `maps Play's Norwegian to Android's Bokmal`() {
        assertThat(resourceDirFor("no-NO")).isEqualTo("values-nb")
    }

    @Test
    fun `maps Filipino to fil rather than tl`() {
        assertThat(resourceDirFor("fil")).isEqualTo("values-fil")
    }

    @Test
    fun `never produces the same resource directory twice`() {
        val dirs = PLAY_TO_RESOURCE_QUALIFIER.keys.map { resourceDirFor(it) }
        // A collision means one locale silently overwrites another's translations.
        assertThat(dirs).containsNoDuplicates()
    }

    @Test
    fun `every BCP-47 tag is well formed`() {
        val wellFormed = Regex("^[a-z]{2,3}(-[A-Z][a-z]{3})?(-([A-Z]{2}|[0-9]{3}))?$")
        PLAY_TO_BCP47.forEach { (play, tag) ->
            assertThat(tag).matches(wellFormed.pattern)
            assertThat(play).isNotEmpty()
        }
    }

    @Test
    fun `rejects a locale the store does not ship`() {
        val thrown = runCatching { resourceDirFor("xx-XX") }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
    }
}
