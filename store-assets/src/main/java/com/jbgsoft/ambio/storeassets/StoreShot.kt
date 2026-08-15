package com.jbgsoft.ambio.storeassets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jbgsoft.ambio.core.domain.model.SoundGlow
import com.jbgsoft.ambio.core.domain.model.gradientOf
import com.jbgsoft.ambio.ui.effects.mixGradientBackground

/**
 * The frame every store image is rendered into: the real mix gradient behind, a
 * localised headline, and the actual app below it.
 *
 * [content] is a real screen, not a mock. That is the whole point of rendering
 * these on the JVM rather than compositing them: the shot cannot claim a layout
 * the app does not have.
 */
@Composable
fun StoreShot(
    caption: String,
    glows: List<SoundGlow>,
    content: @Composable () -> Unit
) {
    val gradient = gradientOf(glows)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .mixGradientBackground { gradient }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 28.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    // Robolectric hands the window no system bars, so
                    // systemBarsPadding() inside the app reserves nothing and the
                    // screen lays out taller than it ever does on a device. These
                    // stand in for the status and gesture bars, so the shot shows
                    // the layout a phone actually produces.
                    .padding(top = SYSTEM_BAR_TOP, bottom = SYSTEM_BAR_BOTTOM),
                contentAlignment = Alignment.TopCenter
            ) {
                content()
            }
        }
    }
}

/** Stand-ins for the insets Robolectric does not provide. */
private val SYSTEM_BAR_TOP = 24.dp
private val SYSTEM_BAR_BOTTOM = 24.dp

/** A plain dark ground for shots whose screen draws its own background. */
@Composable
fun StoreShotSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF11131F))
    ) {
        content()
    }
}
