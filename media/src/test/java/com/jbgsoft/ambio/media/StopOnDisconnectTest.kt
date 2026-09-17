package com.jbgsoft.ambio.media

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StopOnDisconnectTest {

    @Test
    fun `app controller leaving an idle player stops the service`() {
        assertThat(shouldStopOnDisconnect(isNotificationController = false, playbackState = Player.STATE_IDLE))
            .isTrue()
    }

    @Test
    fun `app controller leaving a ready player keeps the service`() {
        // Paused is STATE_READY with playWhenReady false: the notification stays and so do we.
        assertThat(shouldStopOnDisconnect(isNotificationController = false, playbackState = Player.STATE_READY))
            .isFalse()
    }

    @Test
    fun `notification controller leaving an idle player keeps the service`() {
        assertThat(shouldStopOnDisconnect(isNotificationController = true, playbackState = Player.STATE_IDLE))
            .isFalse()
    }

    @Test
    fun `notification controller leaving a ready player keeps the service`() {
        assertThat(shouldStopOnDisconnect(isNotificationController = true, playbackState = Player.STATE_READY))
            .isFalse()
    }
}
