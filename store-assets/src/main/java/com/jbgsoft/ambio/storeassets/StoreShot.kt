package com.jbgsoft.ambio.storeassets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jbgsoft.ambio.core.domain.model.SoundGlow
import com.jbgsoft.ambio.core.domain.model.gradientOf
import com.jbgsoft.ambio.ui.effects.mixGradientBackground

/** Stand-ins for the insets Robolectric does not provide. */
private val SYSTEM_BAR_TOP = 24.dp
private val SYSTEM_BAR_BOTTOM = 24.dp

private val PANEL_CORNER = 26.dp

/**
 * One store image: a slice of a single wide gradient, a localised headline, and
 * the app.
 *
 * The gradient is not drawn per shot. It is laid out [total] canvases wide and
 * shifted left by [index] of them, so shot two continues exactly where shot one
 * stopped and the set reads as one panorama when it is scrolled in the Play
 * gallery. That continuity is the only reason every shot shares one mix.
 *
 * One device per shot. A second panel fills the frame but halves the size of the
 * thing being sold, so pairing is worth it only where a shot has to make two
 * points at once, and none of these do.
 *
 * The panel is a real screen, not a mockup. That is the point of rendering these
 * rather than compositing them: a shot cannot claim a layout the app lacks.
 */
@Composable
fun StoreShot(
    caption: String,
    glows: List<SoundGlow>,
    spec: ShotSpec,
    index: Int,
    total: Int,
    content: @Composable () -> Unit
) {
    val gradient = gradientOf(glows)
    val landscape = spec.widthPx > spec.heightPx

    Box(
        modifier = Modifier
            .fillMaxSize()
            .mixGradientBackground(
                panoramaIndex = index,
                panoramaCount = total
            ) { gradient }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = caption,
                style = if (landscape) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.headlineSmall
                },
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .padding(top = if (landscape) 24.dp else 32.dp, bottom = 12.dp)
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Sized off the height so the whole screen stays in frame. The
                // layouts these shots are selling are the reason workstream A
                // happened; cropping the bottom would cut the transport off again.
                val scale = ((maxHeight - 34.dp) / spec.deviceHeight)
                    .coerceAtMost(if (landscape) 0.86f else 0.64f)

                Panel(
                    width = spec.deviceWidth,
                    height = spec.deviceHeight,
                    scale = scale,
                    // A couple of degrees, alternating, so a scrolled set has some
                    // rhythm without any single shot looking crooked on its own.
                    rotation = if (index % 2 == 0) -1.5f else 1.5f,
                    modifier = Modifier.align(Alignment.TopCenter),
                    content = content
                )
            }
        }
    }
}

/**
 * One device panel: a box of the right shape whose contents believe they are a
 * whole phone or tablet.
 *
 * It works by scaling the density rather than the drawing. A box `width * scale`
 * wide, composed under a density multiplied by the same scale, measures as
 * exactly `width` to everything inside it, so the app picks the layout it would
 * pick on real hardware and the text scales with it.
 *
 * Scaling the drawing instead does not work here: a graphicsLayer transform is
 * applied after layout, so the app still composes at full size and the parent
 * merely crops it, which is what an earlier attempt produced -- fragments of a
 * phone rather than a phone.
 */
@Composable
private fun Panel(
    width: Dp,
    height: Dp,
    scale: Float,
    rotation: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val outer = LocalDensity.current

    Box(
        modifier = modifier
            .size(width * scale, height * scale)
            .graphicsLayer {
                rotationZ = rotation
                shadowElevation = 30f
                shape = RoundedCornerShape(PANEL_CORNER)
                clip = true
            }
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(outer.density * scale, outer.fontScale)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Opaque, so every panel reads as a device. Home paints its own
                    // gradient over this, but the picker draws no background of its
                    // own and without it that shot alone had no edges.
                    .background(MaterialTheme.colorScheme.background)
                    // Robolectric hands the window no system bars, so
                    // systemBarsPadding() inside the app reserves nothing and every
                    // screen lays out taller than it ever does on a device. These
                    // stand in, so the shot shows the layout a phone really produces.
                    .padding(top = SYSTEM_BAR_TOP, bottom = SYSTEM_BAR_BOTTOM)
            ) {
                content()
            }
        }
    }
}
