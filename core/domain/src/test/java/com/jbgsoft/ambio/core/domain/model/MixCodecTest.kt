package com.jbgsoft.ambio.core.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MixCodecTest {

    // Ids and order mirror SoundRepositoryImpl; the resource ids are irrelevant here.
    private val sounds = listOf("rain", "fireplace", "forest", "ocean", "cave")
        .mapIndexed { index, id ->
            Sound(
                id = id,
                nameRes = index,
                icon = Icons.Default.WaterDrop,
                audioRes = index,
                illustrationRes = index,
                theme = SoundTheme.entries[index]
            )
        }

    private fun decode(encoded: String) = MixCodec.decode(encoded, sounds)

    @Test
    fun `a bare id is read as a single sound at full level`() {
        val mix = decode("rain")

        assertThat(mix.map { it.sound.id }).containsExactly("rain")
        assertThat(mix.single().level).isEqualTo(1.0f)
    }

    @Test
    fun `several ids without levels are all read at full level`() {
        val mix = decode("rain,fireplace")

        assertThat(mix.map { it.sound.id }).containsExactly("rain", "fireplace").inOrder()
        assertThat(mix.map { it.level }).containsExactly(1.0f, 1.0f)
    }

    @Test
    fun `levels are read when present`() {
        val mix = decode("rain:1.0,fireplace:0.6")

        assertThat(mix.map { it.level }).containsExactly(1.0f, 0.6f).inOrder()
    }

    @Test
    fun `ids are emitted in the canonical order regardless of activation order`() {
        val mix = decode("cave,rain")

        assertThat(MixCodec.encode(mix, withLevels = false)).isEqualTo("rain,cave")
    }

    @Test
    fun `unknown ids are discarded without dropping the rest`() {
        val mix = decode("rain,wind,ocean")

        assertThat(mix.map { it.sound.id }).containsExactly("rain", "ocean").inOrder()
    }

    @Test
    fun `a string with no usable id falls back to the first sound`() {
        val mix = decode("wind,thunder")

        assertThat(mix.map { it.sound.id }).containsExactly("rain")
        assertThat(mix.single().level).isEqualTo(1.0f)
    }

    @Test
    fun `an empty string falls back to the first sound`() {
        assertThat(decode("").map { it.sound.id }).containsExactly("rain")
    }

    @Test
    fun `levels outside zero to one are clamped when decoded`() {
        val mix = decode("rain:2.5,fireplace:-0.4")

        assertThat(mix.map { it.level }).containsExactly(1.0f, 0.0f).inOrder()
    }

    @Test
    fun `a malformed level falls back to full`() {
        val mix = decode("rain:loud")

        assertThat(mix.single().level).isEqualTo(1.0f)
    }

    @Test
    fun `encoding with levels uses two decimals`() {
        val mix = listOf(ActiveSound(sounds[0], 1.0f), ActiveSound(sounds[1], 0.6f))

        assertThat(MixCodec.encode(mix, withLevels = true)).isEqualTo("rain:1.00,fireplace:0.60")
    }

    @Test
    fun `encoding without levels emits bare ids`() {
        val mix = listOf(ActiveSound(sounds[0], 0.6f))

        assertThat(MixCodec.encode(mix, withLevels = false)).isEqualTo("rain")
    }

    @Test
    fun `encode then decode round-trips`() {
        val original = decode("rain:0.30,ocean:0.75")

        val roundTripped = decode(MixCodec.encode(original, withLevels = true))

        assertThat(roundTripped.map { it.sound.id }).isEqualTo(original.map { it.sound.id })
        assertThat(roundTripped.map { it.level }).isEqualTo(original.map { it.level })
    }

    @Test
    fun `a duplicated id is kept once`() {
        val mix = decode("rain,rain:0.2")

        assertThat(mix.map { it.sound.id }).containsExactly("rain")
    }
}
