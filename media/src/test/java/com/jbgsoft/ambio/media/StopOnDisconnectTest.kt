package com.jbgsoft.ambio.media

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StopOnDisconnectTest {

    private fun decide(
        isAppController: Boolean = true,
        isNotificationController: Boolean = false,
        playbackState: Int = Player.STATE_READY,
        playWhenReady: Boolean = false
    ) = shouldStopOnDisconnect(isAppController, isNotificationController, playbackState, playWhenReady)

    @Test
    fun `app controller leaving an idle player stops the service`() {
        assertThat(decide(playbackState = Player.STATE_IDLE)).isTrue()
    }

    @Test
    fun `app controller leaving a paused player stops the service`() {
        // Ambient mode has no Stop, only Pause; to the person who filed #9 a paused mix
        // is a stopped one, and backing out of it left the service and its notification.
        assertThat(decide(playbackState = Player.STATE_READY, playWhenReady = false)).isTrue()
    }

    @Test
    fun `app controller leaving a playing player keeps the service`() {
        assertThat(decide(playbackState = Player.STATE_READY, playWhenReady = true)).isFalse()
    }

    @Test
    fun `app controller leaving a player that is buffering towards playback keeps the service`() {
        assertThat(decide(playbackState = Player.STATE_BUFFERING, playWhenReady = true)).isFalse()
    }

    @Test
    fun `an idle player stops the service even if it was left set to play`() {
        // Stop releases every track but does not touch playWhenReady.
        assertThat(decide(playbackState = Player.STATE_IDLE, playWhenReady = true)).isTrue()
    }

    @Test
    fun `notification controller leaving never stops the service`() {
        assertThat(decide(isNotificationController = true, playbackState = Player.STATE_IDLE)).isFalse()
        assertThat(decide(isNotificationController = true, playWhenReady = false)).isFalse()
    }

    @Test
    fun `a controller from another app leaving never stops the service`() {
        // System UI, a headset, a watch: they come and go while the app is open.
        assertThat(decide(isAppController = false, playbackState = Player.STATE_IDLE)).isFalse()
        assertThat(decide(isAppController = false, playWhenReady = false)).isFalse()
    }
}
