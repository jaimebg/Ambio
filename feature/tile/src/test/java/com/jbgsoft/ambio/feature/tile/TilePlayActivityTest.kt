package com.jbgsoft.ambio.feature.tile

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.media.AudioService
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TilePlayActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `play intent is a media button aimed at the service`() {
        val intent = playIntent(context)

        assertThat(intent.action).isEqualTo(Intent.ACTION_MEDIA_BUTTON)
        assertThat(intent.component?.className).isEqualTo(AudioService::class.java.name)
    }

    @Test
    fun `play intent carries a plain play key, not play-pause`() {
        // On this path nothing is playing; PLAY_PAUSE on a stopped player would be a
        // no-op toggle in the wrong direction if the flag were stale.
        val key = playIntent(context).getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)

        assertThat(key).isNotNull()
        assertThat(key!!.keyCode).isEqualTo(KeyEvent.KEYCODE_MEDIA_PLAY)
        assertThat(key.action).isEqualTo(KeyEvent.ACTION_DOWN)
    }
}
