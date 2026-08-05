package com.jbgsoft.ambio.feature.widget

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.core.domain.model.mixPalettes
import org.junit.Test

class WidgetDisplayTest {

    private val names = mapOf(1 to "Rain", 2 to "Fireplace", 3 to "Forest")

    private fun sound(id: String, nameRes: Int, theme: SoundTheme) = Sound(
        id = id,
        nameRes = nameRes,
        icon = Icons.Default.WaterDrop,
        audioRes = 0,
        illustrationRes = 0,
        theme = theme
    )

    private val rain = sound("rain", 1, SoundTheme.RAIN)
    private val fireplace = sound("fireplace", 2, SoundTheme.FIREPLACE)
    private val forest = sound("forest", 3, SoundTheme.FOREST)

    private fun display(mix: List<ActiveSound>, isPlaying: Boolean = false) =
        widgetDisplay(
            mix = mix,
            isPlaying = isPlaying,
            names = { names.getValue(it) },
            countLabel = { "$it sounds" }
        )

    @Test
    fun `one sound shows its name`() {
        val d = display(listOf(ActiveSound(rain, 1f)))

        assertThat(d.title).isEqualTo("Rain")
    }

    @Test
    fun `two sounds are joined with a plus`() {
        val d = display(listOf(ActiveSound(rain, 1f), ActiveSound(fireplace, 1f)))

        assertThat(d.title).isEqualTo("Rain + Fireplace")
    }

    @Test
    fun `three or more sounds show a count instead of names`() {
        val d = display(
            listOf(ActiveSound(rain, 1f), ActiveSound(fireplace, 1f), ActiveSound(forest, 1f))
        )

        assertThat(d.title).isEqualTo("3 sounds")
    }

    @Test
    fun `the palette is the mix of the active sounds' themes`() {
        val d = display(listOf(ActiveSound(rain, 1f), ActiveSound(fireplace, 1f)))

        assertThat(d.palette).isEqualTo(mixPalettes(listOf(SoundTheme.RAIN, SoundTheme.FIREPLACE)))
    }

    @Test
    fun `a single sound keeps its own hand-tuned palette`() {
        val d = display(listOf(ActiveSound(rain, 1f)))

        assertThat(d.palette).isEqualTo(mixPalettes(listOf(SoundTheme.RAIN)))
    }

    @Test
    fun `the volume levels do not affect the palette`() {
        val loud = display(listOf(ActiveSound(rain, 1f), ActiveSound(fireplace, 1f)))
        val quiet = display(listOf(ActiveSound(rain, 0.1f), ActiveSound(fireplace, 0.9f)))

        assertThat(quiet.palette).isEqualTo(loud.palette)
    }

    @Test
    fun `the playing flag is carried through`() {
        assertThat(display(listOf(ActiveSound(rain, 1f)), isPlaying = true).isPlaying).isTrue()
        assertThat(display(listOf(ActiveSound(rain, 1f)), isPlaying = false).isPlaying).isFalse()
    }

    @Test
    fun `an empty mix falls back to the default palette rather than crashing`() {
        // The repository guarantees a non-empty mix, but the widget can render before
        // DataStore has been read on a cold start, and a widget must never crash the
        // launcher.
        val d = display(emptyList())

        assertThat(d.title).isEmpty()
        assertThat(d.palette).isEqualTo(mixPalettes(listOf(SoundTheme.RAIN)))
    }
}
