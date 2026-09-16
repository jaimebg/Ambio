package com.jbgsoft.ambio.feature.tile

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TileTapTest {

    @Test
    fun `while playing, a tap is a broadcast toggle`() {
        assertThat(TileTap.decide(isPlaying = true)).isEqualTo(TileTap.BROADCAST_TOGGLE)
    }

    @Test
    fun `while not playing, a tap launches play through an activity`() {
        assertThat(TileTap.decide(isPlaying = false)).isEqualTo(TileTap.LAUNCH_PLAY)
    }
}
