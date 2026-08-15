package com.jbgsoft.ambio.storeassets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * The frame every store image is rendered into: the real mix gradient behind, a
 * localised headline, and two device panels showing the app.
 *
 * Two panels rather than one, angled and overlapping, because a single flat
 * screenshot wastes most of a 9:16 canvas and says nothing about the app running
 * anywhere else. The pair reads as one panorama and lets a shot make two points
 * at once -- the mixer and what it is mixing, the timer and the mix behind it.
 *
 * Both panels are real screens, not mockups. That is the point of rendering
 * these rather than compositing them: a shot cannot claim a layout the app does
 * not have.
 */
@Composable
fun StoreShot(
    caption: String,
    glows: List<SoundGlow>,
    spec: ShotSpec,
    back: @Composable () -> Unit,
    front: @Composable () -> Unit
) {
    val gradient = gradientOf(glows)
    val landscape = spec.widthPx > spec.heightPx

    Box(
        modifier = Modifier
            .fillMaxSize()
            .mixGradientBackground { gradient }
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
                    .padding(top = if (landscape) 22.dp else 30.dp, bottom = 10.dp)
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                if (landscape) {
                    LandscapePair(spec, back, front)
                } else {
                    PortraitPair(spec, back, front)
                }
            }
        }
    }
}

/**
 * Two phones, the second raised behind the first.
 *
 * Sized off the height, not the width: the whole screen has to be in frame,
 * because the layouts these shots are selling are the reason workstream A
 * happened at all. Cropping the bottom would cut the transport off again.
 */
@Composable
private fun BoxWithConstraintsScope.PortraitPair(
    spec: ShotSpec,
    back: @Composable () -> Unit,
    front: @Composable () -> Unit
) {
    // Both panels stay inside the canvas. Letting them bleed off the edge looked
    // dynamic in the abstract and simply cut the mix bar off in practice, which
    // is the control this release exists to keep on screen.
    //
    // The margins clear the rotation, not just the panel: turning a 420dp panel
    // by four degrees swings its corners about 15dp wider than its box, which at
    // a 10dp margin sheared the corner off the one behind.
    val frontScale = ((maxHeight - 30.dp) / spec.deviceHeight).coerceAtMost(0.56f)
    val backScale = frontScale * 0.82f

    DevicePanel(
        spec = spec,
        scale = backScale,
        rotation = 4f,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = (-24).dp, y = 10.dp),
        content = back
    )
    DevicePanel(
        spec = spec,
        scale = frontScale,
        rotation = -3f,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .offset(x = 22.dp, y = (-12).dp),
        content = front
    )
}

/** A tablet beside a phone: the same app, both layouts, in one frame. */
@Composable
private fun BoxWithConstraintsScope.LandscapePair(
    spec: ShotSpec,
    back: @Composable () -> Unit,
    front: @Composable () -> Unit
) {
    val tabletScale = ((maxHeight - 28.dp) / spec.deviceHeight).coerceAtMost(0.78f)
    val phoneScale = ((maxHeight - 20.dp) / PHONE_H).coerceAtMost(0.46f)

    DevicePanel(
        spec = spec,
        scale = tabletScale,
        rotation = -2f,
        modifier = Modifier
            .align(Alignment.CenterStart)
            .offset(x = 12.dp),
        content = front
    )
    Panel(
        width = PHONE_W,
        height = PHONE_H,
        scale = phoneScale,
        rotation = 4f,
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .offset(x = (-30).dp),
        content = back
    )
}

private val PHONE_W = 411.dp
private val PHONE_H = 914.dp

@Composable
private fun DevicePanel(
    spec: ShotSpec,
    scale: Float,
    rotation: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) = Panel(spec.deviceWidth, spec.deviceHeight, scale, rotation, modifier, content)

/**
 * One device panel: a box of the right shape whose contents believe they are a
 * whole phone or tablet.
 *
 * It works by scaling the density rather than the drawing. A box
 * `deviceWidth * scale` wide, composed under a density multiplied by the same
 * scale, measures as exactly `deviceWidth` to everything inside it, so the app
 * picks the layout it would pick on real hardware and the text scales with it.
 *
 * Scaling the drawing instead does not work here: a graphicsLayer transform is
 * applied after layout, so the app still composes at full size and the parent
 * just crops it, which is what the first attempt produced -- fragments of a
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
