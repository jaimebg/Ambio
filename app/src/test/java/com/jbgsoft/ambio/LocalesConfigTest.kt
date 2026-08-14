package com.jbgsoft.ambio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.common.resources.PLAY_TO_BCP47
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

// SDK pinned to 34: Robolectric 4.16.1 supports at most 36, and its API 36
// shadow needs Java 21 while this toolchain is 17.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalesConfigTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun declaredTags(): List<String> {
        val tags = mutableListOf<String>()
        val parser = context.resources.getXml(R.xml.locales_config)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                val name = parser.getAttributeValue(
                    "http://schemas.android.com/apk/res/android",
                    "name"
                )
                if (name != null) tags.add(name)
            }
        }
        return tags
    }

    @Test
    fun `declares exactly the locales the store listing ships`() {
        assertThat(declaredTags()).containsExactlyElementsIn(PLAY_TO_BCP47.values)
    }

    @Test
    fun `declares no duplicates`() {
        assertThat(declaredTags()).containsNoDuplicates()
    }
}
