package com.jbgsoft.ambio.core.domain.model

import androidx.compose.ui.graphics.Color

enum class SoundTheme(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color
) {
    RAIN(
        primary = Color(0xFF6481EB),
        onPrimary = Color(0xFF1A1F3C),
        secondary = Color(0xFF8B9DC3),
        background = Color(0xFF1A1F3C),
        surface = Color(0xFF252B4A),
        surfaceVariant = Color(0xFF2E3555)
    ),
    FIREPLACE(
        primary = Color(0xFFE85D04),
        onPrimary = Color(0xFF2D1810),
        secondary = Color(0xFFFAA307),
        background = Color(0xFF2D1810),
        surface = Color(0xFF3D2216),
        surfaceVariant = Color(0xFF4D2E1E)
    ),
    FOREST(
        primary = Color(0xFF44A178),
        onPrimary = Color(0xFF1B2E1F),
        secondary = Color(0xFF52B788),
        background = Color(0xFF1B2E1F),
        surface = Color(0xFF243C2A),
        surfaceVariant = Color(0xFF2D4A35)
    ),
    OCEAN(
        primary = Color(0xFF0087CE),
        onPrimary = Color(0xFF0A1929),
        secondary = Color(0xFF48CAE4),
        background = Color(0xFF0A1929),
        surface = Color(0xFF132F4C),
        surfaceVariant = Color(0xFF1A3D5C)
    ),
    CAVE(
        primary = Color(0xFF927D6C),
        onPrimary = Color(0xFF1C1816),
        secondary = Color(0xFF9C8A7C),
        background = Color(0xFF1C1816),
        surface = Color(0xFF2A2420),
        surfaceVariant = Color(0xFF38302A)
    ),

    /**
     * White and brown noise. Deliberately the only desaturated palette: noise is
     * the one sound here with no place attached to it, so a hue would imply a
     * scene that is not there.
     *
     * The values are cool rather than plain grey to hold it apart from CAVE,
     * which is the nearest neighbour in both roles. Measured against the closest
     * pair already shipping (FIREPLACE/CAVE backgrounds, distance 18.0), this
     * sits at 19.9 from CAVE's background and 87.7 from RAIN's primary — so it
     * is no less distinct than the palettes that already coexist.
     */
    NOISE(
        primary = Color(0xFFA3B0C4),
        onPrimary = Color(0xFF0B0E13),
        secondary = Color(0xFFBAC5D2),
        background = Color(0xFF0B0E13),
        surface = Color(0xFF151A21),
        surfaceVariant = Color(0xFF20272F)
    )
}
