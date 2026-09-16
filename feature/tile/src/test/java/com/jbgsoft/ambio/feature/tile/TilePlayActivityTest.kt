package com.jbgsoft.ambio.feature.tile

import android.content.Context
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
    fun `play intent is aimed at the service`() {
        val intent = playIntent(context)

        assertThat(intent.component?.className).isEqualTo(AudioService::class.java.name)
    }

    @Test
    fun `play intent carries the stored-mix action`() {
        val intent = playIntent(context)

        assertThat(intent.action).isEqualTo(AudioService.ACTION_PLAY_STORED_MIX)
    }
}
